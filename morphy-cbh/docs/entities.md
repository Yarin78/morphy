# Entities

ChessBase stores _entities_ such as players, tournaments, annotators etc in a similar way. All entities of a specific _type_ is stored in a file on its own; for instance players are stored in the .cbp file and tournaments in the .cbt file.

Entities are always referenced from games using integer ids. Id 0 is the first entity in the file, id 1 the second entity and so on.

It's generally not possible to explicitly add an entity; entities are always added (and deleted) as a side effect of adding games that references them.
Additions occur when adding a new game that references the entity for the first time, and deletions occur automatically when there are no more references to an entity. 

For this to be efficient, along with each entity is stored some basic statistics:
the number of references to it, and the id of the first game with the reference.
This makes it easy to know if an entity can be removed.

## Entity types

These are the different entity types in ChessBase:

| Type | File | Default sorting order | Signed/unsigned
| --- | --- | --- | ---
| <a href="players.md">Players</a>     | .cbp | Last name, first name | unsigned 
| <a href="tournaments.md">Tournaments</a> | .cbt | Year (desc), Title | signed
| <a href="annotators.md">Annotators</a>  | .cbc | Name | signed
| <a href="sources.md">Sources</a>     | .cbs | Title | signed
| <a href="teams.md">Teams</a>       | .cbe | ? | unsigned
| <a href="game_tags.md">Game Tags</a>   | .cbl | ? |

Each entity has a "default sorting order" (see [Entity index](#entity_index) below).
This is the order the entities are listed in when browsing an entity type in ChessBase.

All strings are encoded as 1 byte per character, so when sorting strings, the sorting
is based on the encoded byte stream. No fancy unicode string sorting is used.
This also means the entities are sorted case sensitively. 


Some entity types used signed bytes (-127 to 128) while others used unsigned bytes (0 to 255), which affects the sorting order when non-ASCII characters are used (typically international letters). 

## <a name="entity_index">Entity index</a>

Within the entity files is also an index that allows quick search of an entity based on its primary key.
For instance, the player entities are physically in the file sorted by id order (typically insertion order), but the index tree allows quick lookups if you know the name of a player without having to iterate through the whole file.
This is accomplished by embedding a _binary tree_ in the file; each entity is a node in the tree, and all nodes in the left subtree
represents entities that come before the current node in the default sorting order, and all nodes in the right subtree represents entities that come after the current node.

For instance, here is an example of a player file with 5 players stored physically on disk in the order they were inserted,
but where additional metadata for each record stores the id of the children to that tree node. The root node of tree is stored in the file header.  

| Id | Name | Left child Id | Right child Id
| --- | --- | --- | ---
| 0 | Carlsen, M | -1 | -1
| 1 | Nakamura, H | -1 | -1
| 2 (root) | Caruana, F | 3 | 4
| 3 | Aronian, L | -1 | 0
| 4 | So, W | 1 | -1

By traversing the nodes in the binary tree in order from left to right, we get the id order `3, 0, 2, 1, 4` which happens to be the players sorting by name.

### Deletions

If an entity is deleted from the database, the contents of the file can't easily be shifted. The id will instead be marked as logically deleted.
It's indicate by storing `-999` as the id of the left child at that tree node. The right child id is either `-1` or a reference to another deleted entity id.
This creates a linked list of deleted entities, where the id of the first entity is stored in the file header. 
When a new entity is added, ChessBase first checks if there exists a logically deleted entity that can be reused before adding a new entity to the end of the file.

### Balanced tree

A binary tree is only effective if it's balanced. The tree in the entity file uses an [AVL tree](https://en.wikipedia.org/wiki/AVL_tree) to automatically rebalance the tree.
This requires one additional byte of metadata for each tree node: the height difference between the left and the right subtree.
This provides enough information to [rebalance the tree](https://en.wikipedia.org/wiki/AVL_tree#Rebalancing) using rotations when adding, deleting or changing the entites in a way that affects the sorting order. 


## Entity file format

Each entity file starts with a short header (28 or 32 bytes) with some metadata about the file. This includes
the size of the actual data record, pointers to the root of the binary tree, pointer to the first deleted entity etc.
Then follows a record for each entity. The first 9 bytes in each record stores information about the binary tree.

Integers are stored in Little Endian unless otherwise specified. 

| Offset | Bytes | Description
| --- | --- | --- 
| 0 | 4 | Number of entity records in this file (the _capacity_).
| 4 | 4 | The id to the entity that is the root in the binary tree. `-1` _or_ `0` if the database is empty and has no records.
| 8 | 4 | The integer `1234567890`
| 12 | 4 | The size of an entity record (excluding the 9 bytes for storing metadata about the binary tree node)
| 16 | 4 | The id of a deleted entity record. `-1` if there are no deleted records in this file. 
| 20 | 4 | Number of entities in this file (excluding deleted ones).
| 24 | 4 | Additional bytes left of this header file after reading this value. In older databases this is `0` meaning the header is 28 bytes; in newer bases it's `4` so the header is 32 bytes. 

The additional header bytes are usually 0 but sometimes set but with unclear purpose.

The header is immediately follow by the entity records. Each entity record has the following format.

| Offset | Bytes | Description
| --- | --- | ---
| 0 | 4 | The id of the entity that is the left subtree of this node, or -1 if there is none. 
| 4 | 4 | The id of the entity that is the right subtree of this node, or -1 if there is none.
| 8 | 1 | The height difference between the left and the right subtree. Should be either 1 (left subtree is 1 level higher), 0 or 1 in a well balanced tree.
| 9 | ? | The remaining data for that specific entity type, see below.

All entities optionally have [Search Boosters](search_boosters.md).
