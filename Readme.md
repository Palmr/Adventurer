# Adventurer #
This is a program written to analyse choose your own adventure books.

More specifically [To Be Or Not To Be](https://www.kickstarter.com/projects/breadpig/to-be-or-not-to-be-that-is-the-adventure/), by [Ryan North](https://twitter.com/ryanqnorth).
I claim no ownership of his most excellent book and **I won't be including the PDF version in this repository**.

The original Kickstarter offered a poster visualising a graph of all the routes through the book, which you can still buy, but I wanted more than a paper-based graph.

Adventurer results in a [Neo4j](http://neo4j.com/) database filled with information parsed out of the PDF. With that you can query all the routes you could take to get from one page to another, routes to end pages, and more!

Some might say this takes the fun out of a choose your own adventure book, and it kind of does. But some days you just want to end up being Hamlet riding Claudius like a skateboard after being fired out of a cannon from the rad pirate ship you took control of after fighting the pirate captain (check out page 197).

## Example ##
Once the code has run and analysed the book you can start Neo4j in the graph-db directory and start querying the data with Cypher.

You can then query out the full graph of pages that make up the sub-book (The Murder of Gonzago):

![Sub Book graph rendered by Neo4j](./images/sub-book.gif)

Cypher = `MATCH (n:SubBook) RETURN n`

Here's some of the route that ends up at my favourite ending, page 197 (The fight with the pirate captain is at the bottom with multiple routes as you choose how to swashbuckle):

![Part of the route leading to page 197](./images/pirate-route.gif)

Cypher = `MATCH r=(s:Page)-[*..20]->(e:Page {book_page_label: "197"}) RETURN r`

You can also view the whole book in graph form, though this can take some time to render nicely:

![The entire book graph](./images/entire-book.gif)

Cypher = `MATCH n RETURN n`

## Requirements ##
Adventurer is written in Java (1.8+) with Maven for dependency management. You will need [Neo4j Community Edition](http://neo4j.com/download/)

You will need to supply your own copy of **To Be Or Not To Be** however. Everyone who backed the project on [Kickstarter](https://www.kickstarter.com/) should have relieved a copy. I'm not sure if you can buy a PDF copy today, if not I would highly suggest buying a legit paper copy before heading to Google looking for a less legit copy. This program can help you enjoy the book more, and the book is well worth the money!

## TODO ##
- Improve page parsing for end/image pages
- Attempt to work back from links to find the choice text to add to relationships
- Weight relationships with wordcount of the page they're going (could then write queries to get the long route or quick routes)
- Consolidate page parse loops
- Research indexing in Neo4J to improve performance
