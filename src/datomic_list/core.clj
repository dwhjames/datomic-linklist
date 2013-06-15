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


(ns datomic-list.core
  (:require
    [datomic.api :as d]))

(defn is-list-empty
  "Test if list is empty"
  [db list-id]
  (-> db (d/entity list-id) (get :linklist/first) keyword?))

(defn nodes
  "Get list nodes"
  [list-entity]
  (loop [n (:linklist/first list-entity) l []]
    (if (keyword? n)
      l
      (recur (:linknode/next n) (conj l n)))))

(defn elems
  "Get list elements"
  [list-entity]
  (map #(get % :linknode/elem) (nodes list-entity)))

(def find-nodes-query
  '[:find ?node
    :in $ ?list
    :where [?node :linknode/list ?list]])

(defn find-nodes
  "query for all nodes in a list"
  [db list-id]
  (d/q find-nodes-query db list-id))

(def find-last-node-query
  '[:find ?node
    :in $ ?list
    :where
      [?node :linknode/next :linknode/empty]
      [?node :linknode/list ?list]])

(defn find-last-node
  "query for last node in a list"
  [db list-id]
  (ffirst (d/q find-last-node-query db list-id)))

(def find-node-by-list-and-elem-query
  '[:find ?node
    :in $ ?list ?elem
    :where
      [?node :linknode/elem ?elem]
      [?node :linknode/list ?list]])

(defn find-node-by-list-and-elem
  "query for a node by list and elem"
  [db list-id elem-id]
  (ffirst (d/q find-node-by-list-and-elem-query db list-id elem-id)))

(defn prepend-elem-tx
  "tx data to prepend an element to a list"
  [db list-id elem-id]
  (let [node-id (d/tempid :db.part/user)
        first-node-id (-> db (d/entity list-id) (get-in [:linklist/first :db/id]))]
    [{:db/id node-id
      :linknode/elem elem-id
      :linknode/list list-id
      :linknode/next first-node-id}
     [:db/add list-id :linklist/first node-id]]))

(defn append-elem-tx
  "tx data to append an element to a list"
  [db list-id elem-id]
  (let [last-id (find-last-node db list-id)]
    (if last-id
      (let [node-id (d/tempid :db.part/user)]
        [{:db/id node-id
          :linknode/elem elem-id
          :linknode/list list-id
          :linknode/next :linknode/empty}
         [:db/add last-id :linknode/next node-id]])
      (prepend-elem-tx list-id elem-id))))

(defn insert-elem-tx
  "tx data to insert an element in a list"
  [db list-id anchor-elem-id elem-id]
  (let [anchor-node-id (find-node-by-list-and-elem db list-id anchor-elem-id)
        after-entity (-> db (d/entity anchor-node-id) (get :linknode/next))
        node-id (d/tempid :db.part/user)]
    [{:db/id node-id
      :linknode/elem elem-id
      :linknode/list list-id
      :linknode/next (:db/id after-entity)}
     [:db/add anchor-node-id :linknode/next node-id]]))

(defn nodes-tx
  "partial tx data for chaining elems with a sequence of nodes"
  [list-id elem-ids]
  (let [node-ids (repeatedly (count elem-ids) #(d/tempid :db.part/user))
        nodes-tx (for [[node-id elem-id] (map vector node-ids elem-ids)]
                   {:db/id node-id
                    :linknode/elem elem-id
                    :linknode/list list-id})
        chain-tx (for [[node-id next-id] (map vector node-ids (next node-ids))]
                    [:db/add node-id :linknode/next next-id])]
    [node-ids (concat nodes-tx chain-tx)]))

(defn prepend-elems-tx
  "tx data to prepend elements to a list"
  [db list-id elem-ids]
  (assert (not (empty? elem-ids)))
  (let [[node-ids node-chain-tx] (nodes-tx list-id elem-ids)
         first-tx [:db/add list-id :linklist/first (first node-ids)]
         old-first-id (-> db (d/entity list-id) (get-in [:linklist/first :db/id]))
         link-new-old-tx [:db/add (last node-ids) :linknode/next old-first-id]]
    (conj node-chain-tx first-tx link-new-old-tx)))


(defn append-elems-tx
  "tx data to append elements to a list"
  [db list-id elem-ids]
  (assert (not (empty? elem-ids)))
  (let [[node-ids node-chain-tx] (nodes-tx list-id elem-ids)
        last-tx [:db/add (last node-ids) :linknode/next :linknode/empty]
        old-last-id (find-last-node db list-id)
        link-old-new-tx (if old-last-id
                          [:db/add old-last-id :linknode/next (first node-ids)]
                          [:db/add list-id :linklist/first (first node-ids)])]
    (conj node-chain-tx last-tx link-old-new-tx)))

(defn insert-elems-tx
  "tx data to insert elements into a list after an anchor"
  [db list-id anchor-elem-id elem-ids]
  (assert (not (empty? elem-ids)))
  (let [[node-ids node-chain-tx] (nodes-tx list-id elem-ids)
        anchor-node-id (find-node-by-list-and-elem db list-id anchor-elem-id)
        after-node-id (-> db (d/entity anchor-node-id) (get-in [:linknode/next :db/id]))
        anchor-head-tx [:db/add anchor-node-id :linknode/next (first node-ids)]
        last-after-tx [:db/add (last node-ids) :linknode/next after-node-id]]
    (conj node-chain-tx anchor-head-tx last-after-tx)))

(def find-before-and-after-query
  '[:find ?before ?after
    :in $ ?node
    :where
      [?before :linknode/next ?node]
      [?node   :linknode/next ?after]])

(defn find-before-and-after
  "query for the nodes before and after the given node"
  [db node-id]
  (first (d/q find-before-and-after-query db node-id)))

(defn unlink-first-elem-tx
  "tx data to set new first to be old second"
  [db list-id first-node]
  [[:db/add list-id :linklist/first (get-in first-node [:linknode/next :db/id])]])

(defn remove-first-elem-tx
  "tx data to remove the first element of the list"
  [db list-id]
  (let [first-node (-> db (d/entity list-id) (get :linklist/first))]
    (if (keyword? first-node)
      (throw (Exception. "can't remove first of empty list"))
      (conj
        (unlink-first-elem-tx db list-id first-node)
        [:db.fn/retractEntity (:db/id first-node)]))))

(defn unlink-node-tx
  "tx data to unlink the given node"
  [db list-id node-id]
  (let [first-node (-> db (d/entity list-id) (get :linklist/first))]
    (cond
      (keyword? first-node)
        (throw (Exception. "can't remove from empty list"))
      (= node-id (get first-node :db/id))
        (unlink-first-elem-tx db list-id first-node)
      :else
        (let [[before-id after-id] (find-before-and-after db node-id)]
          [[:db/add before-id :linknode/next after-id]]))))

(defn remove-elem-tx
  "tx data to remove an element of the list"
  [db list-id elem-id]
  (let [node-id (find-node-by-list-and-elem db list-id elem-id)]
    (conj
      (unlink-node-tx db list-id node-id)
      [:db.fn/retractEntity node-id])))

(defn relink-nodes-tx
  "tx data to relink nodes to skip deleted nodes"
  [node-ids delete-set]
  (loop [[last-id penul-id & rest-ids :as ids] (reverse node-ids)
         next-id :linknode/empty
         ops []]
    (cond
      (nil? last-id) ; empty, return accum
        [next-id ops]
      (delete-set last-id)
        (cond
          (nil? penul-id) ; delete singleton, return accum
            [next-id ops]
          (delete-set penul-id) ; delete last and penultimate
            (recur (rest ids) next-id ops)
          :else ; delete last, keep penultimate
            (recur rest-ids penul-id (conj ops [:db/add penul-id :linknode/next next-id])))
      :else ; keep last
        (recur (rest ids) last-id ops))))

(def find-nodes-by-elems-query
  '[:find ?node
    :in $ ?list [?elem ...]
    :where
      [?node :linknode/elem ?elem]
      [?node :linknode/list ?list]])

(defn find-nodes-by-elems
  "query for a set of nodes by a list and set of elems"
  [db list-id elem-ids]
  (set (map first (d/q find-nodes-by-elems-query db list-id elem-ids))))

(defn remove-elems-tx
  "tx data to remove elems"
  [db list-id elem-ids]
  (let [delete-set (find-nodes-by-elems db list-id elem-ids)
        list-entity (d/entity db list-id)
        node-ids (map #(get % :db/id) (nodes list-entity))
        [first-id relink-tx] (relink-nodes-tx node-ids delete-set)
        first-in-list (get-in list-entity [:linklist/first :db/id])]
    (concat
      (if (delete-set first-in-list) [[:db/add list-id :linklist/first first-id]] [])
      (map #(vector :db.fn/retractEntity %) delete-set)
      relink-tx)))

(defn remove-and-prepend-elems-tx
  "tx data to remove one collection of elems and prepend another collection of elems"
  [db list-id delete-elem-ids new-elem-ids]
  (let [delete-set (find-nodes-by-elems db list-id delete-elem-ids)
        list-entity (d/entity db list-id)
        node-ids (map #(get % :db/id) (nodes list-entity))
        [first-id relink-tx] (relink-nodes-tx node-ids delete-set)
        [new-node-ids new-node-chain-tx] (nodes-tx list-id new-elem-ids)]
    (concat
      [[:db/add list-id :linklist/first (first new-node-ids)]
       [:db/add (last new-node-ids) :linknode/next first-id]]
      (map #(vector :db.fn/retractEntity %) delete-set)
      relink-tx)))

(defn move-elem-tx
  "tx data to move an elem after another elem"
  [db list-id elem-id anchor-elem-id]
  (let [node-id (find-node-by-list-and-elem db list-id elem-id)
        anchor-node-id (find-node-by-list-and-elem db list-id anchor-elem-id)]
    (if (= node-id anchor-node-id)
      (throw (Exception. "node can't have itself as its next node"))
      (let [after-node (-> db (d/entity anchor-node-id) (get :linknode/next))]
        (if (= node-id (get after-node :db/id)) ;; false if after-node is :linknode/empty
          [] ; no work to be done
          (conj
            (unlink-node-tx db list-id node-id)
            [:db/add anchor-node-id :linknode/next node-id]
            [:db/add node-id :linknode/next (or (get after-node :db/id) after-node)]))))))

(defn move-elem-first-tx
  "tx data to move an elem to the start of the list"
  [db list-id elem-id]
  (let [node-id (find-node-by-list-and-elem db list-id elem-id)
        first-node-id (-> db (d/entity list-id) (get-in [:linklist/first :db/id]))]
    (if (= node-id first-node-id)
      [] ; nothing to do, already first
      (conj
        (unlink-node-tx db list-id node-id)
        [:db/add node-id :linknode/next first-node-id]
        [:db/add list-id :linklist/first node-id]))))
