;;   Copyright 2014 Pellucid Analytics
;;
;;   Licensed under the Apache License, Version 2.0 (the "License");
;;   you may not use this file except in compliance with the License.
;;   You may obtain a copy of the License at
;;
;;       http://www.apache.org/licenses/LICENSE-2.0
;;
;;   Unless required by applicable law or agreed to in writing, software
;;   distributed under the License is distributed on an "AS IS" BASIS,
;;   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;;   See the License for the specific language governing permissions and
;;   limitations under the License.


(ns datomic-list.core-test
  (:use clojure.test
        datomic-list.core)
  (:require
    [datomic.api :as d]))

(def uri "datomic:mem://linkedlist")

(def list-schema-tx (read-string (slurp "src/datomic_list/list_schema.edn")))
(def test-schema-tx (read-string (slurp "test/datomic_list/test_schema.edn")))

(defn db-global-fixture
  "a test fixture for Datomic"
  [f]
  (d/create-database uri)
  (println "database " uri " created")
  (let [conn (d/connect uri)]
    @(d/transact conn (concat list-schema-tx test-schema-tx)))
  (f)
  (d/delete-database uri)
  (println "database " uri " deleted")
  (d/shutdown true))

(use-fixtures :once db-global-fixture)

(def test-data-tx (read-string (slurp "test/datomic_list/example_list.edn")))

(deftest test-empty-list
  (let [e (d/tempid :db.part/user)
        tx (-> uri d/connect
                   d/db
                   (d/with [[:db/add e :linklist/first :linknode/empty]]))
        db (:db-after tx)
        list-id (d/resolve-tempid db (:tempids tx) e)]
    (testing "an empty list"

      (testing "is empty"
        (is (is-list-empty db list-id)))

      (testing "has no nodes"
        (let [l (-> db (d/entity list-id))]
          (is (empty? (nodes l)))
          (is (empty? (find-nodes db list-id)))))

      (testing "has no last node"
        (is (nil? (find-last-node db list-id)))))))

(deftest test-static-list
  (let [db (-> uri d/connect d/db (d/with test-data-tx) (get :db-after))
        list-id (ffirst (d/q '[:find ?e :where [?e :linklist/first]] db))]
    (testing "list with elems A B C D E"

      (testing "is not empty"
        (is (not (is-list-empty db list-id)))))))

(deftest test-relink-nodes-tx
  (testing "relink-nodes-tx"

    (testing "delete nothing"
      (let [[first-id ops] (relink-nodes-tx [1 2 3] #{})]
        (is (= 1 first-id))
        (is (empty? ops))))

    (testing "delete all nodes"
      (let [[first-id ops] (relink-nodes-tx [1 2 3] #{1 2 3})]
        (is (= :linknode/empty first-id))
        (is (empty? ops))))

    (testing "delete first node"
      (let [[first-id ops] (relink-nodes-tx [1 2 3] #{1})]
        (is (= 2 first-id))
        (is (empty? ops))))

    (testing "delete last node"
      (let [[first-id ops] (relink-nodes-tx [1 2 3] #{3})]
        (is (= 1 first-id))
        (is (= [[:db/add 2 :linknode/next :linknode/empty]] ops))))

    (testing "delete middle node of three"
      (let [[first-id ops] (relink-nodes-tx [1 2 3] #{2})]
        (is (= 1 first-id))
        (is (= [[:db/add 1 :linknode/next 3]] ops))))

    (testing "delete first two nodes"
      (let [[first-id ops] (relink-nodes-tx [1 2 3] #{1 2})]
        (is (= 3 first-id))
        (is (empty? ops))))

    (testing "delete last two nodes"
      (let [[first-id ops] (relink-nodes-tx [1 2 3] #{2 3})]
        (is (= 1 first-id))
        (is (= [[:db/add 1 :linknode/next :linknode/empty]] ops))))

    (testing "delete middle two nodes of four"
      (let [[first-id ops] (relink-nodes-tx [1 2 3 4] #{2 3})]
        (is (= 1 first-id))
        (is (= [[:db/add 1 :linknode/next 4]] ops))))

    (testing "delete outer two nodes of four"
      (let [[first-id ops] (relink-nodes-tx [1 2 3 4] #{1 4})]
        (is (= 2 first-id))
        (is (= [[:db/add 3 :linknode/next :linknode/empty]] ops))))

    (testing "delete first and third of four"
      (let [[first-id ops] (relink-nodes-tx [1 2 3 4] #{1 3})]
        (is (= 2 first-id))
        (is (= [[:db/add 2 :linknode/next 4]] ops))))

    (testing "delete second and fourth of four"
      (let [[first-id ops] (relink-nodes-tx [1 2 3 4] #{2 4})]
        (is (= 1 first-id))
        (is (= 2 (count ops)))
        (is (some #(= [:db/add 1 :linknode/next 3] %) ops))
        (is (some #(= [:db/add 3 :linknode/next :linknode/empty] %) ops))))))

