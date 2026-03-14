# Query engine

A Query is a tree of interconnected QueryNodes where QueryData is pulled through using the stream() method.
The QueryPlanner constructs several query trees from a logical query, and based on statistics determine
which one is most likely to be executed the quickest.

## Query data

In a traditional SQL-like query system, each row is an arbitrary dict of field-values.

In this query engine, each row is semi-strongly type: it returns a stream of QueryData<T> where T is an entity type that has an integer id.
QueryData<T> is a tuple (int id, T data, Object extra) where the id always correspond to the id of the data,
although in some cases data may be null if it's not yet been loaded (e.g. we might have retried the id from an index).
The extra field can be an arbitrary object - it's typically the right hand side of a join and is used to filter/sort on.

## Query nodes

A Query Node always has a type T that matches with the QueryData it outputs.

There are three types of query nodes: Source nodes, Join nodes and Transformation nodes.

### Source nodes

A source streams data from a table or an index (or more generally, from a storage). Examples:

* TableScan of GameHeaders, ExtendedGameHeaders, Players, Tournaments, TournamentExtra etc. The table corresponds
  to the type of the QueryData and the scan is always in id order (or reverse). It's more efficient to use a TableScan
  than an IdIndexScan because data is fetched in batches.
* GameHeaderIdIndexScan - get a GameHeader by id
* ExtendedGameHeaderIdIndexScan - get an ExtendedGameHeader by id
* EntityIdIndexScan - get an Entity (Player, Tournament, Annotator etc) by id
* TournamentExtraIndexScan - get a TournamentExtra by id
* EntityIndexScan - get an entity based on their default sorting order (see @entities.md), e.g. a player by their last name.
* GameEntityIndexScan - get game ids given an entity id
* ManualQueryNode - returns a pre-determined list of QueryData. Mainly used in tests.

An IndexScans gives the Loop join (see below) the opportunity to only stream the relevant part of the index (typically a direct lookup of an id).

Any source node may have a filter that determines what data should be returned.
In case of IndexScans, the id range is typically not part of the filter but rather
a parameter to the stream function, since an index may be looped over multiple times
with different parameters (in a Loop join, see below).

### Join nodes

A join combines data from two other nodes (left and right) into one output stream. The left hand side of the join
always matches the type of the QueryNode - that is, the output type of a join is the same as the left hand input type.
The right hand side can be of another type.

All joins are equijoins. Each join takes as input a function that maps its data to the key the join comparison is done with.
Such a key may in cases return more than one element - e.g. when joining games with players, often you want to find players that are either white or black, so two keys are given from the left hand side.

There are three semantically different types of joins: Inner (natural) join, semi-join and anti-joins.
An inner join produces all combinations of values from both inputs that matches the join condition.
A semi-join returns at one row from the left hand side if there is at least one matching entry from the right side.
An anti-join is the opposite of semi-join and only returns a row from the left hand side if there is no match on the right side.

The returned QueryData of a join differs depending on the inputs. If the type of the right hand side differs from the left hand side,
the extra field in QueryData will contain the entity from the right hand side. If the type is the same,
the existing extra data will be retained, but the data field will get set if possible (e.g. if the left hand only has ids,
but the right has id+data, the output will have id+data). In case of a semi-join, only one value from the right hand side
will be kept (the first found).

There are three types of join algorithms: Loop joins, Hash joins and Merge joins. Semantically they do the same thing,
but the performance differs depending on the inputs. All join algorithms can perform the three semantically different joins.

* A merge join requires that both sides are sorted on the join key in the same direction and that neither side has duplicates. 
  Typically the type for both sides are the same, or at the very least that the key is the same (e.g. the types could 
  a GameHeader and an ExtendedGameHeader since both have the game id as the natural key).

* A hash join requires that the right hand side contains no duplicates on the join key.

* A loop join requires that the right hand side can be iterated over multiple times. 
  Typically the right hand side is an index that can be quickly iterated on based on the join key.

### Transformation nodes

Each transformation node takes exactly one other QueryNode as input.
It performs no data reads from storage, but only data transformation of the input data.

* Distinct - ensures that at most one item per id in the QueryData is returned. Requires that the input is sorted by the id (in either direction).
* Sort - sorts the input data in the given SortOrder. 
* Limit - limits the response to the top N items
* Map - performs an arbitrary transformation of the data. 
  This node contains two type parameters as it may change the type of the output from the input,
  for instance by moving the extra field in the primary data field. If the input and output
  type are the same, the sort order is preserved. Otherwise it's undefined (unsorted).
* Filter - filters data based on a specified predicate. Note that filtering should almost
  always be done at the Source node. Special filter nodes should rarely have to be used.

## Query node metadata

Query nodes have some metadata fields that gives hints to the consuming node what it can expect of the output data.
This includes:

* Sort order - which order will the data be returned? (see below for more on sort orders)
* Duplicates - can there be duplicates in the output stream?

## Sort orders

A sort order is a list of sort fields and a corresponding list if the field should be reversed or not.
A SortField<T> maps a QueryData<T> to a comparable. It's up to the query planner to ensure
that the QueryData is able to provide this value (e.g. in case of the extra field being accessed).
