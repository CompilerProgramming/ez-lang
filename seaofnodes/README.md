## Sea of Nodes IR 

This module uses the [Simple](https://github.com/SeaOfNodes/Simple) project as a Sea of Nodes
backend. We translate from the EeZee AST to Sea of Nodes IR.

For copyright info on Simple project please see above website.

### Implementation Notes

* The code was derived from original chapter 21 and 22 before all the refactoring.
* We do not intend to support methods.
* The mechanism for adding dependencies is not used, instead iterative peeps potentially do more work.
* Inlining is disabled.
* Struct types are not structural as in upstream, they are nominal in EZ Lang.