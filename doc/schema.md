## a linked list

```clojure
{:db/id #db/id[:db.part/db]
 :db/ident :linklist/first
 :db/valueType :db.type/ref
 :db/cardinality :db.cardinality/one
 :db/doc "The first node of this list"
 :db.install/_attribute :db.part/db}
```


## a linked node

```clojure
{:db/id #db/id[:db.part/db]
 :db/ident :linknode/next
 :db/valueType :db.type/ref
 :db/cardinality :db.cardinality/one
 :db/doc "The node after this node"
 :db.install/_attribute :db.part/db}

{:db/id #db/id[:db.part/db]
 :db/ident :linknode/list
 :db/valueType :db.type/ref
 :db/cardinality :db.cardinality/one
 :db/doc "The list this node belongs to"
 :db.install/_attribute :db.part/db}
```

## the empty node

```clojure
[:db/add #db/id[:db.part/user] :db/ident :linknode/empty]
```