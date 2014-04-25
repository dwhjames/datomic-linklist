## API for datomic-linklist

Namespace `linklist.fn`

***

### :linklist.fn/prepend
transaction function

Usage: `[:linklist.fn/prepend list-id elem-id]`

Transaction function to prepend an element to a list. Takes a database
value, an entity id for a list, and an entity id to prepend.

Throws an `IllegalStateException` if the element to prepend is already
in the list.


***

### :linklist.fn/find-last-node
database function

Usage: `(d/invoke db :linklist.fn/prepend db list-id)`

Database function to find the last node of a list. Takes a database
value and an entity id for a list

***

### :linklist.fn/append
transaction function

Usage: `[:linklist.fn/append list-id elem-id]`

Transaction function to append an element to a list Takes a database
value, an entity id for a list, and an entity id to append.

Throws an `IllegalStateException` if the element to append is already
in the list.


***

### :linklist.fn/insert
transaction function

Usage: `[:linklist.fn/insert list-id anchor-id elem-id]`

Transaction function to insert an element into a list. Takes a
database value, an entity id for a list, an entity id that is already
present in the list, and an entity id to insert. The entity that is
already present in the list will serve as the anchor for the
insertion.

Throws an `IllegalStateException` if the element to insert is already
in the list. Throws an `IllegalStateException` if the anchor element
is not in the list.


***

### :linklist.fn/initialize
transaction function

Usage: `[:linklist.fn/initialize list-id elem-ids]`

Transaction function to initialize a list. Takes a database value, a
temp id for a list, and a sequence of temp ids for the initial
elements.


***

### :linklist.fn/prepend-many
transaction function

Usage: `[:linklist.fn/prepend-many list-id elem-ids]`

Transaction function to prepend elements to a list. Takes a database
value, an entity id for a list, and a sequence of entity ids to be
prepended to the list.

Throws an `IllegalArgumentException` if the sequence of elements to
prepend is empty. Throws an `IllegalStateException` if any of the
elements to prepend are already in the list.


***

### :linklist.fn/append-many
transaction function

Usage: `[:linklist.fn/append-many list-id elem-ids]`

Transaction function to append elements to a list. Takes a database
value, an entity id for a list, and a sequence of entity ids to be
appended to the list.

Throws an `IllegalArgumentException` if the sequence of elements to
append is empty. Throws an `IllegalStateException` if any of the
elements to append are already in the list.


***

### :linklist.fn/insert-many
transaction function

Usage: `[:linklist.fn/insert-many list-id anchor-id elem-ids]`

Transaction function to insert elements into a list after an anchor.
Takes a database value, an entity id for a list, a entity id that is
already present in the list, and a sequence of entity ids to be
inserted into the list. The elements will be inserted after the anchor
element.

Throws an `IllegalArgumentException` if the sequence of elements to
insert is empty. Throws an `IllegalStateException` if any of the
elements to insert are already in the list. Throws an
`IllegalStateException` if the anchor element is not in the list.


***

### :linklist.fn/remove-first
transaction function

Usage: `[:linklist.fn/remove-first list-id]`

Transaction function to remove the first element of the list. Takes a
database value and an entity id for a list. The first entity in the
list will be unlinked: this will only retract the linklist specific
attributes on the entity.

Throws an `IllegalStateException` if the list is empty.


***

### :linklist.fn/find-before-and-after
database function

Usage: `(d/invoke db :linklist.fn/find-before-and-after db node-id)`

Database function to query for the nodes before and after the given
node. Takes a database value and an entity id for a list element. If
they exist, this query will find the elements that come before and
after in the list.


***

### :linklist.fn/remove
transaction function

Usage: `[:linklist.fn/remove list-id elem-id]`

Transaction function to remove an element of the list. Takes a
database value, and entity id for a list, and an entity id for an
element to unlink from the list (this will only retract the linklist
specific attributes on the entity).

Throws an `IllegalStateException` if the element to remove is not in
the list.


***

### :linklist.fn/list-nodes
database function

Usage `(d/invoke db :linklist.fn/list-nodes list-entity)`

Database function to enumerate the entities in a list. Take a entity
map of a list, and returns a vector of the element entities in order.


***

### :linklist.fn/remove-many
transaction function

Usage: `[:linklist.fn/remove-many list-id elem-ids]`

Transaction function to remove many elements from a list. Takes a
database value, an entity id of a list, and a collection of entity ids
to unlink from the list (this will only retract the linklist specific
attributes on the entities). The elements to remove do _not_ need to
be contiguous in the list.

Throws an `IllegalStateException` if any of the elements to remove are
not in the list.


***

### :linklist.fn/remove-and-prepend-many
transaction function

Usage: `[:linklist.fn/remove-and-prepend-many list-id delete-elem-ids new-elem-ids]`

Transaction function to remove one collection of elems and prepend
another collection of elems. Takes a database value, an entity id of a
list, a collection of entity ids to remove from the list, and a
collection of entity ids to prepend to the list. The elements to
remove do _not_ need to be contiguous in the list.

Throws an `IllegalStateException` if any of the elements to remove are
not in the list. Throws an `IllegalStateException` if any of the
elements to prepend are already in the list.


***

### :linklist.fn/move-first
transaction function

Usage: `[:linklist.fn/move-first list-id elem-id]`

Transaction function to move an elem to the start of the list. Takes a
database value, an entity id for a list, and an entity id of an
element of the list. The given element will be moved to the beginning
of the list.

Throws an `IllegalStateException` if the element to move is not
already in the list.


***

### :linklist.fn/move
transaction function

Usage: `[:linklist.fn/move list-id elem-id anchor-id]`

Transaction function to move an elem after another elem. Takes a
database value, an entity id for a list, an entity id of an element to
move, and an entity id of an element to use as an anchor. The element
to move will be placed directly after the anchor.

Throws an `IllegalStateException` if the element to move is not
already in the list. Throws an `IllegalStateException` if the anchor
element is not already in the list. Throws an
`IllegalArgumentException` if the element to move and the anchor
element are the same.
