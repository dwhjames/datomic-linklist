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

;; test how elements are chained together
(deftest test-chain-elems-tx
  (let [db (-> uri d/connect d/db)
        chain-elems (partial d/invoke db :linklist.fn/chain-elems)]
    (testing "chain-elems-tx"

      (testing "with no elements"
        (is (= 0
               (count (chain-elems 0 [])))))

      (testing "with one element"
        (is (= [[:db/add 1 :linknode/list 0]]
               (chain-elems 0 [1]))))

      (testing "with two elements"
        (let [chain-tx (chain-elems 0 [1 2])]
          (is (= 3
                 (count chain-tx)))
          (are [elem-id]
               (some #(= [:db/add elem-id :linknode/list 0]
                         %)
                     chain-tx)
               1 2)
          (is (some #(= [:db/add 1 :linknode/next 2]
                        %)
                    chain-tx))))

      (testing "with three elements"
        (let [chain-tx (chain-elems 0 [1 2 3])]
          (is (= 5
                 (count chain-tx)))
          (are [elem-id]
               (some #(= [:db/add elem-id :linknode/list 0]
                         %)
                     chain-tx)
               1 2 3)
          (are [elem-id next-id]
               (some #(= [:db/add elem-id :linknode/next next-id]
                         %)
                     chain-tx)
               1 2
               2 3))))))

;; test how elements are relinked after deletion
(deftest test-relink-nodes-tx
  (let [db (-> uri d/connect d/db)
        relink-nodes (partial d/invoke db :linklist.fn/relink-nodes)]
    (testing "relink-nodes"

      (testing "relink nothing"
        (let [[first-id ops] (relink-nodes [] #{})]
          (is (= :linknode/empty first-id))
          (is (empty? ops))))

      (testing "delete nothing"
        (let [[first-id ops] (relink-nodes [1 2 3] #{})]
          (is (= 1 first-id))
          (is (empty? ops))))

      (testing "delete all nodes"
        (let [[first-id ops] (relink-nodes [1 2 3] #{1 2 3})]
          (is (= :linknode/empty first-id))
          (is (empty? ops))))

      (testing "delete first node"
        (let [[first-id ops] (relink-nodes [1 2 3] #{1})]
          (is (= 2 first-id))
          (is (empty? ops))))

      (testing "delete last node"
        (let [[first-id ops] (relink-nodes [1 2 3] #{3})]
          (is (= 1 first-id))
          (is (= [[:db/add 2 :linknode/next :linknode/empty]]
                 ops))))

      (testing "delete middle node of three"
        (let [[first-id ops] (relink-nodes [1 2 3] #{2})]
          (is (= 1 first-id))
          (is (= [[:db/add 1 :linknode/next 3]]
                 ops))))

      (testing "delete first two nodes"
        (let [[first-id ops] (relink-nodes [1 2 3] #{1 2})]
          (is (= 3 first-id))
          (is (empty? ops))))

      (testing "delete last two nodes"
        (let [[first-id ops] (relink-nodes [1 2 3] #{2 3})]
          (is (= 1 first-id))
          (is (= [[:db/add 1 :linknode/next :linknode/empty]]
                 ops))))

      (testing "delete middle two nodes of four"
        (let [[first-id ops] (relink-nodes [1 2 3 4] #{2 3})]
          (is (= 1 first-id))
          (is (= [[:db/add 1 :linknode/next 4]] ops))))

      (testing "delete outer two nodes of four"
        (let [[first-id ops] (relink-nodes [1 2 3 4] #{1 4})]
          (is (= 2 first-id))
          (is (= [[:db/add 3 :linknode/next :linknode/empty]]
                 ops))))

      (testing "delete first and third of four"
        (let [[first-id ops] (relink-nodes [1 2 3 4] #{1 3})]
          (is (= 2 first-id))
          (is (= [[:db/add 2 :linknode/next 4]]
                 ops))))

      (testing "delete second and fourth of four"
        (let [[first-id ops] (relink-nodes [1 2 3 4] #{2 4})]
          (is (= 1 first-id))
          (is (= 2 (count ops)))
          (is (some #(= [:db/add 1 :linknode/next 3]
                        %)
                    ops))
          (is (some #(= [:db/add 3 :linknode/next :linknode/empty]
                        %)
                    ops)))))))


(deftest test-tx-fns
  (testing ":linklist.fn/prepend"
    (let [l (d/tempid :db.part/user)
          {:keys [tempids db-after]}
            (-> uri
                d/connect
                d/db
                (d/with [[:db/add l :linklist/first :linknode/empty]]))
          list-id (d/resolve-tempid db-after tempids l)
          e (d/tempid :db.part/user)
          tx-report
            (d/with db-after
                    [[:db/add e :elem/data "A"]
                     [:linklist.fn/append list-id e]])]
      (is (= "A" (-> tx-report
                     :db-after
                     (d/entity list-id)
                     nodes
                     first
                     :elem/data))))))

(deftest initialize-list
  (let [db (-> uri d/connect d/db)
        list-id (d/tempid :db.part/user)]
    (testing "empty initialize"
      (is (= [[:db/add list-id :linklist/first :linknode/empty]]
             (d/invoke db :linklist.fn/initialize db list-id []))))

    (testing "non-empty initialize"
      (let [elem-ids (repeatedly 5 #(d/tempid :db.part/user))
            tx (d/invoke db :linklist.fn/initialize db list-id elem-ids)]
        (is (= 3 (count tx)))
        (are [a] #(= a %) tx
          [:linklist.fn/chain-elems list-id elem-ids]
          [:db/add list-id :linklist/first (first elem-ids)]
          [:db/add (last elem-ids) :linknode/next :linknode/empty])))))

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
        (let [l (d/entity db list-id)]
          (is (empty? (d/invoke db :linklist.fn/list-nodes l)))))

      (testing "has no last node"
        (is (nil? (d/invoke db :linklist.fn/find-last-node db list-id))))

      (testing "can be prepended to"
        ;; prepend one element
        (let [elem-id (d/tempid :db.part/user)
              tx (d/invoke db :linklist.fn/prepend db list-id elem-id)]
          (is (= 2 (count tx)))
          (is (some #(= [:db/add list-id :linklist/first elem-id] %) tx))
          (let [entity-tx (some #(when (get % :db/id) %) tx)]
            (is (= list-id (:linknode/list entity-tx)))
            (is (= :linknode/empty (:linknode/next entity-tx)))))

        ;; prepend a singleton vector of elements
        (let [elem-id (d/tempid :db.part/user)
              tx (d/invoke db :linklist.fn/prepend-many db list-id [elem-id])]
          (is (= 3 (count tx)))
          (are [a] #(= a %) tx
            [:linklist.fn/chain-elems list-id [elem-id]]
            [:db/add list-id :linklist/first elem-id]
            [:db/add elem-id :linknode/next :linknode/empty]))

        ;; prepend a singleton vector of elements
        (let [elem-id (d/tempid :db.part/user)
              tx (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [] [elem-id])]
          (is (= 3 (count tx)))
          (are [a] #(= a %) tx
            [:linklist.fn/chain-elems list-id [elem-id]]
            [:db/add list-id :linklist/first elem-id]
            [:db/add elem-id :linknode/next :linknode/empty]))

        ;; prepend two elements
        (let [[elem-1 elem-2] (repeatedly #(d/tempid :db.part/user))
              tx (d/invoke db :linklist.fn/prepend-many db list-id [elem-1 elem-2])]
          (is (= 3 (count tx)))
          (are [a] #(= a %) tx
            [:linklist.fn/chain-elems list-id [elem-1 elem-2]]
            [:db/add list-id :linklist/first elem-1]
            [:db/add elem-2 :linknode/next :linknode/empty]))

        ;; prepend two elements
        (let [[elem-1 elem-2] (repeatedly #(d/tempid :db.part/user))
              tx (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [] [elem-1 elem-2])]
          (is (= 3 (count tx)))
          (are [a] #(= a %) tx
            [:linklist.fn/chain-elems list-id [elem-1 elem-2]]
            [:db/add list-id :linklist/first elem-1]
            [:db/add elem-2 :linknode/next :linknode/empty])))

      (testing "can be appended to"
        ;; append one element
        (let [elem-id (d/tempid :db.part/user)
              tx (d/invoke db :linklist.fn/append db list-id elem-id)]
          (is (= 1 (count tx)))
          (is (= [[:linklist.fn/prepend list-id elem-id]] tx)))

        ;; append a singleton vector of elements
        (let [elem-id (d/tempid :db.part/user)
              tx (d/invoke db :linklist.fn/append-many db list-id [elem-id])]
          (is (= 3 (count tx)))
          (are [a] #(= a %) tx
            [:linklist.fn/chain-elems list-id [elem-id]]
            [:db/add list-id :linklist/first elem-id]
            [:db/add elem-id :linknode/next :linknode/empty]))

        ;; append two elements
        (let [[elem-1 elem-2] (repeatedly #(d/tempid :db.part/user))
              tx (d/invoke db :linklist.fn/append-many db list-id [elem-1 elem-2])]
          (is (= 3 (count tx)))
          (are [a] #(= a %) tx
            [:linklist.fn/chain-elems list-id [elem-1 elem-2]]
            [:db/add list-id :linklist/first elem-1]
            [:db/add elem-2 :linknode/next :linknode/empty])))

      (testing "has no first elem to remove"
        (is (thrown? IllegalStateException
                     (d/invoke db :linklist.fn/remove-first db list-id)))))))


(deftest test-static-list
  (let [db (-> uri d/connect d/db (d/with test-data-tx) (get :db-after))
        list-id (ffirst (d/q '[:find ?e :where [?e :linklist/first]] db))]
    (testing "list with elems A B C D E"

      (testing "is not empty"
        (is (not (is-list-empty db list-id))))

      (testing "has list of elems [A B C D E]"
        (let [l (->>
                  (d/entity db list-id)
                  (d/invoke db :linklist.fn/list-nodes)
                  (map :elem/data))]
          (is (= ["A" "B" "C" "D" "E"] l))))

      (testing "has last elem E"
        (is (= "E"
               (->> list-id
                    (d/invoke db :linklist.fn/find-last-node db)
                    (d/entity db)
                    :elem/data))))

      (let [first-elem (-> db
                           (d/entity list-id)
                           (get :linklist/first))
            first-id (:db/id first-elem)
            second-id (get-in first-elem [:linknode/next :db/id])
            third-id (get-in first-elem [:linknode/next :linknode/next :db/id])
            fourth-id (get-in first-elem [:linknode/next :linknode/next :linknode/next :db/id])
            last-id (get-in first-elem [:linknode/next :linknode/next :linknode/next :linknode/next :db/id])
            empty-id (d/entid db :linknode/empty)]

        (testing "can't have one its exisiting elements prepended, appended, or inserted"
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/prepend db list-id first-id)))
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/prepend-many db list-id [first-id])))
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/append db list-id first-id)))
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/append-many db list-id [first-id])))
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/insert db list-id first-id last-id)))
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/insert-many db list-id first-id [last-id])))
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [first-id] [second-id]))))

        (testing "must use an element in the list to anchor an insert"
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/insert db list-id (d/tempid :db.part/user) (d/tempid :db.part/user))))
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/insert-many db list-id (d/tempid :db.part/user) [(d/tempid :db.part/user)]))))

        (testing "has a before and after for each elem"
          ;; test all permutations of finding the before and after
          (are [b n a]
               (let [[before after] (d/invoke db :linklist.fn/find-before-and-after db n)]
                 (and (= b before)
                      (= a after)))
               nil       first-id  nil
               first-id  second-id third-id
               second-id third-id  fourth-id
               third-id  fourth-id last-id
               fourth-id last-id   empty-id))

        (testing "must prepend, appended, or insert a non-zero number of elements"
          (is (thrown? IllegalArgumentException
                       (d/invoke db :linklist.fn/prepend-many db list-id [])))
          (is (thrown? IllegalArgumentException
                       (d/invoke db :linklist.fn/append-many db list-id [])))
          (is (thrown? IllegalArgumentException
                       (d/invoke db :linklist.fn/insert-many db list-id first-id []))))

        (testing "can be prepended to"
          ;; prepend one element
          (let [elem-id (d/tempid :db.part/user)
                tx (d/invoke db :linklist.fn/prepend db list-id elem-id)]
            (is (= 2 (count tx)))
            (is (some #(= [:db/add list-id :linklist/first elem-id] %) tx))
            (let [entity-tx (some #(when (get % :db/id) %) tx)]
              (is (= list-id (:linknode/list entity-tx)))
              (is (= first-id (:linknode/next entity-tx)))))

          ;; prepend a singleton seq of elements
          (let [elem-id (d/tempid :db.part/user)
                tx (d/invoke db :linklist.fn/prepend-many db list-id [elem-id])]
            (is (= 3 (count tx)))
            (are [a] #(= a %) tx
              [:linklist.fn/chain-elems list-id [elem-id]]
              [:db/add list-id :linklist/first elem-id]
              [:db/add elem-id :linknode/next first-id]))

          ;; prepend a singleton seq of elements
          (let [elem-id (d/tempid :db.part/user)
                tx (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [] [elem-id])]
            (is (= 3 (count tx)))
            (are [a] #(= a %) tx
              [:linklist.fn/chain-elems list-id [elem-id]]
              [:db/add list-id :linklist/first elem-id]
              [:db/add elem-id :linknode/next first-id]))

          ;; prepend two elements
          (let [[elem-1 elem-2] (repeatedly #(d/tempid :db.part/user))
                tx (d/invoke db :linklist.fn/prepend-many db list-id [elem-1 elem-2])]
            (is (= 3 (count tx)))
            (are [a] #(= a %) tx
              [:linklist.fn/chain-elems list-id [elem-1 elem-2]]
              [:db/add list-id :linklist/first elem-1]
              [:db/add elem-2 :linknode/next first-id]))

          ;; prepend two elements
          (let [[elem-1 elem-2] (repeatedly #(d/tempid :db.part/user))
                tx (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [] [elem-1 elem-2])]
            (is (= 3 (count tx)))
            (are [a] #(= a %) tx
              [:linklist.fn/chain-elems list-id [elem-1 elem-2]]
              [:db/add list-id :linklist/first elem-1]
              [:db/add elem-2 :linknode/next first-id])))

        (testing "can be appended to"
          ;; append one element
          (let [elem-id (d/tempid :db.part/user)
                tx (d/invoke db :linklist.fn/append db list-id elem-id)]
            (is (= 2 (count tx)))
            (is (some #(= [:db/add last-id :linknode/next elem-id] %) tx))
            (let [entity-tx (some #(when (get % :db/id) %) tx)]
              (is (= list-id (:linknode/list entity-tx)))
              (is (= :linknode/empty (:linknode/next entity-tx)))))

          ;; append a singleton vector of elements
          (let [elem-id (d/tempid :db.part/user)
                tx (d/invoke db :linklist.fn/append-many db list-id [elem-id])]
            (is (= 3 (count tx)))
            (are [a] #(= a %) tx
              [:linklist.fn/chain-elems list-id [elem-id]]
              [:db/add last-id :linknode/next elem-id]
              [:db/add elem-id :linknode/next :linknode/empty]))

          ;; append two elements
          (let [[elem-1 elem-2] (repeatedly #(d/tempid :db.part/user))
                tx (d/invoke db :linklist.fn/append-many db list-id [elem-1 elem-2])]
            (is (= 3 (count tx)))
            (are [a] #(= a %) tx
              [:linklist.fn/chain-elems list-id [elem-1 elem-2]]
              [:db/add last-id :linknode/next elem-1]
              [:db/add elem-2 :linknode/next :linknode/empty])))

        (testing "can be inserted into"
          ;; insert one element after the first element
          (let [elem-id (d/tempid :db.part/user)
                tx (d/invoke db :linklist.fn/insert db list-id first-id elem-id)]
            (is (= 2 (count tx)))
            (is (some #(= [:db/add first-id :linknode/next elem-id] %) tx))
            (let [entity-tx (some #(when (get % :db/id) %) tx)]
              (is (= list-id (:linknode/list entity-tx)))
              (is (= second-id (:linknode/next entity-tx)))))

          ;; insert a singleton vector of elements after the first element
          (let [elem-id (d/tempid :db.part/user)
                tx (d/invoke db :linklist.fn/insert-many db list-id first-id [elem-id])]
            (is (= 3 (count tx)))
            (are [a] #(= a %) tx
              [:linklist.fn/chain-elems list-id [elem-id]]
              [:db/add last-id :linknode/next elem-id]
              [:db/add elem-id :linknode/next second-id]))

          ;; insert two elements after the first element
          (let [[elem-1 elem-2] (repeatedly #(d/tempid :db.part/user))
                tx (d/invoke db :linklist.fn/insert-many db list-id first-id [elem-1 elem-2])]
            (is (= 3 (count tx)))
            (are [a] #(= a %) tx
              [:linklist.fn/chain-elems list-id [elem-1 elem-2]]
              [:db/add last-id :linknode/next elem-1]
              [:db/add elem-2 :linknode/next second-id])))

        (testing "can be inserted into at the end (like append)"
          ;; insert one element after the last element
          (let [elem-id (d/tempid :db.part/user)
                tx (d/invoke db :linklist.fn/insert db list-id last-id elem-id)]
            (is (= 2 (count tx)))
            (is (some #(= [:db/add last-id :linknode/next elem-id] %) tx))
            (let [entity-tx (some #(when (get % :db/id) %) tx)]
              (is (= list-id (:linknode/list entity-tx)))
              (is (= :linknode/empty (:linknode/next entity-tx)))))

          ;; insert a singleton vector of elements after the last element
          (let [elem-id (d/tempid :db.part/user)
                tx (d/invoke db :linklist.fn/insert-many db list-id last-id [elem-id])]
            (is (= 3 (count tx)))
            (are [a] #(= a %) tx
              [:linklist.fn/chain-elems list-id [elem-id]]
              [:db/add last-id :linknode/next elem-id]
              [:db/add elem-id :linknode/next :linknode/empty]))

          ;; insert two elements after the last element
          (let [[elem-1 elem-2] (repeatedly #(d/tempid :db.part/user))
                tx (d/invoke db :linklist.fn/insert-many db list-id last-id [elem-1 elem-2])]
            (is (= 3 (count tx)))
            (are [a] #(= a %) tx
              [:linklist.fn/chain-elems list-id [elem-1 elem-2]]
              [:db/add last-id :linknode/next elem-1]
              [:db/add elem-2 :linknode/next :linknode/empty])))

        (testing "can only have its own elements removed"
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/remove db list-id (d/tempid :db.part/user))))
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/remove-many db list-id [first-id (d/tempid :db.part/user)])))
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [first-id (d/tempid :db.part/user)] [(d/tempid :db.part/user)]))))

        (testing "can have no elements removed"
          ;; remove no elements
          (let [tx (d/invoke db :linklist.fn/remove-many db list-id [])]
            (is (empty? tx)))

          ;; remove no elements (nor prepend anything)
          (let [tx (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [] [])]
            (is (empty? tx))))

        (testing "can have its first element removed"
          ;; remove first element
          (let [tx (d/invoke db :linklist.fn/remove-first db list-id)]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add list-id :linklist/first second-id]
              [:db/retract first-id :linknode/list list-id]
              [:db/retract first-id :linknode/next second-id]))

          ;; remove first element explicitly
          (let [tx (d/invoke db :linklist.fn/remove db list-id first-id)]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add list-id :linklist/first second-id]
              [:db/retract first-id :linknode/list list-id]
              [:db/retract first-id :linknode/next second-id]))

          ;; remove singleton vector of first element
          (let [tx (d/invoke db :linklist.fn/remove-many db list-id [first-id])]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add list-id :linklist/first second-id]
              [:db/retract first-id :linknode/list list-id]
              [:db/retract first-id :linknode/next second-id]))

          ;; remove singleton vector of first element (and nothing to prepend)
          (let [tx (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [first-id] [])]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add list-id :linklist/first second-id]
              [:db/retract first-id :linknode/list list-id]
              [:db/retract first-id :linknode/next second-id])))

        (testing "can have its last element removed"
          ;; remove last element
          (let [tx (d/invoke db :linklist.fn/remove db list-id last-id)]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add fourth-id :linknode/next empty-id]
              [:db/retract last-id :linknode/list list-id]
              [:db/retract last-id :linknode/next empty-id]))

          ;; remove singleton vector of last element
          (let [tx (d/invoke db :linklist.fn/remove-many db list-id [last-id])]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add fourth-id :linknode/next :linknode/empty]
              [:db/retract last-id :linknode/list list-id]
              [:db/retract last-id :linknode/next :linknode/empty]))

          ;; remove singleton vector of last element (and nothing to prepend)
          (let [tx (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [last-id] [])]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add fourth-id :linknode/next :linknode/empty]
              [:db/retract last-id :linknode/list list-id]
              [:db/retract last-id :linknode/next :linknode/empty])))

        (testing "can have an element removed"
          ;; remove second
          (let [tx (d/invoke db :linklist.fn/remove db list-id second-id)]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add first-id :linknode/next third-id]
              [:db/retract second-id :linknode/list list-id]
              [:db/retract second-id :linknode/next third-id]))

          ;; remove singleton vector of second element
          (let [tx (d/invoke db :linklist.fn/remove-many db list-id [second-id])]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add first-id :linknode/next third-id]
              [:db/retract second-id :linknode/list list-id]
              [:db/retract second-id :linknode/next third-id]))

          ;; remove singleton vector of second element (and nothing to prepend)
          (let [tx (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [second-id] [])]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add first-id :linknode/next third-id]
              [:db/retract second-id :linknode/list list-id]
              [:db/retract second-id :linknode/next third-id])))

        (testing "can have two elements removed"
          ;; remove first and second
          (let [tx (d/invoke db :linklist.fn/remove-many db list-id [first-id second-id])]
            (is (= 5 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add list-id :linklist/first third-id]
              [:db/retract first-id :linknode/list list-id]
              [:db/retract first-id :linknode/next second-id]
              [:db/retract second-id :linknode/list list-id]
              [:db/retract second-id :linknode/next third-id]))

          ;; remove first and second (and delete none)
          (let [tx (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [first-id second-id] [])]
            (is (= 5 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add list-id :linklist/first third-id]
              [:db/retract first-id :linknode/list list-id]
              [:db/retract first-id :linknode/next second-id]
              [:db/retract second-id :linknode/list list-id]
              [:db/retract second-id :linknode/next third-id]))

          ;; remove first and third
          (let [tx (d/invoke db :linklist.fn/remove-many db list-id [first-id third-id])]
            (is (= 6 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add list-id :linklist/first second-id]
              [:db/add second-id :linknode/next fourth-id]
              [:db/retract first-id :linknode/list list-id]
              [:db/retract first-id :linknode/next second-id]
              [:db/retract third-id :linknode/list list-id]
              [:db/retract third-id :linknode/next fourth-id]))

          ;; remove first and third (and delete none)
          (let [tx (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [first-id third-id] [])]
            (is (= 6 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add list-id :linklist/first second-id]
              [:db/add second-id :linknode/next fourth-id]
              [:db/retract first-id :linknode/list list-id]
              [:db/retract first-id :linknode/next second-id]
              [:db/retract third-id :linknode/list list-id]
              [:db/retract third-id :linknode/next fourth-id]))

          ;; remove second and fourth
          (let [tx (d/invoke db :linklist.fn/remove-many db list-id [second-id fourth-id])]
            (is (= 6 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add first-id :linknode/next third-id]
              [:db/add third-id :linknode/next last-id]
              [:db/retract second-id :linknode/list list-id]
              [:db/retract second-id :linknode/next third-id]
              [:db/retract fourth-id :linknode/list list-id]
              [:db/retract fourth-id :linknode/next last-id]))

          ;; remove second and fourth (and delete none)
          (let [tx (d/invoke db :linklist.fn/remove-and-prepend-many db list-id [second-id fourth-id] [])]
            (is (= 6 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add first-id :linknode/next third-id]
              [:db/add third-id :linknode/next last-id]
              [:db/retract second-id :linknode/list list-id]
              [:db/retract second-id :linknode/next third-id]
              [:db/retract fourth-id :linknode/list list-id]
              [:db/retract fourth-id :linknode/next last-id])))

        (testing "can only have its own elements moved"
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/move-first db list-id (d/tempid :db.part/user))))
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/move db list-id (d/tempid :db.part/user) first-id))))

        (testing "can have an element moved to the start"
          ;; move first to first (no op)
          (let [tx (d/invoke db :linklist.fn/move-first db list-id first-id)]
            (is (empty? tx)))

          ;; move second to first
          (let [tx (d/invoke db :linklist.fn/move-first db list-id second-id)]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add first-id :linknode/next third-id]
              [:db/add second-id :linknode/next first-id]
              [:db/add list-id :linklist/first second-id]))

          ;; move last to first
          (let [tx (d/invoke db :linklist.fn/move-first db list-id last-id)]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add fourth-id :linknode/next empty-id]
              [:db/add last-id :linknode/next first-id]
              [:db/add list-id :linklist/first last-id])))

        (testing "can only have its own elements as a move anchor"
          (is (thrown? IllegalStateException
                       (d/invoke db :linklist.fn/move db list-id first-id (d/tempid :db.part/user)))))

        (testing "can't have an element moved after itself"
          (is (thrown? IllegalArgumentException
                       (d/invoke db :linklist.fn/move db list-id first-id first-id))))

        (testing "can have an element moved after an anchor"
          ;; move second after first (no op)
          (let [tx (d/invoke db :linklist.fn/move db list-id second-id first-id)]
            (is (empty? tx)))

          ;; move first after second
          (let [tx (d/invoke db :linklist.fn/move db list-id first-id second-id)]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add list-id :linklist/first second-id]
              [:db/add second-id :linknode/next first-id]
              [:db/add first-id :linknode/next third-id]))

          ;; move second after third
          (let [tx (d/invoke db :linklist.fn/move db list-id second-id third-id)]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add first-id :linknode/next third-id]
              [:db/add third-id :linknode/next second-id]
              [:db/add second-id :linknode/next fourth-id]))

          ;; move third after first (same as move second after third)
          (let [tx (d/invoke db :linklist.fn/move db list-id third-id first-id)]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add first-id :linknode/next third-id]
              [:db/add third-id :linknode/next second-id]
              [:db/add second-id :linknode/next fourth-id]))

          ;; move first after last
          (let [tx (d/invoke db :linklist.fn/move db list-id first-id last-id)]
            (is (= 3 (count tx)))
            (are [a] (some #(= a %) tx)
              [:db/add list-id :linklist/first second-id]
              [:db/add last-id :linknode/next first-id]
              [:db/add first-id :linknode/next :linknode/empty])))

        ))))
