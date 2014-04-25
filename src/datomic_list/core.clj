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
