The EeZee Programming Language
==============================

The EeZee programming language is a toy language with just enough features to allow
experimenting with various compiler techniques.

The base language is intentionally very small. Eventually there will be extended versions
that allow functional and  object oriented paradigms.

Language features
-----------------
* User defined functions
* Integer type
* Floating point type
* User defined ``struct`` types
* One dimensional arrays
* Basic control flow such as ``if`` and ``while`` statements

Keywords
--------
Following are keywords in the language::

    func var new Int Float struct if else while break continue return null

Source Unit
-----------

The EeZee language does not have the concept of modules or imports. Each source file must be
self contained.

There is no predefined ``main`` function in a source unit. The runtime should allow
any defined function to be invoked by supplying appropriate arguments.

Types
-----

The only primitive types in the language are the integer type ``Int`` and the floating point type ``Float``.
The sizes of these types are unspecified, the default implementation sets them as 64-bits in size.

There is not a distinct boolean type, non-zero integer values evaluate as true, and zero evaluates as false.

Users can define one-dimensional arrays and structs.

Arrays and structs are implicitly reference types, i.e. instances of these types are
allocated on the heap.

The language does not specify whether the heap is garbage collected or manually managed, it is
up to the implementation.

A ``struct`` type is a named aggregate with one or more fields. Fields may be of any supported
type. Struct types are nominal, i.e. each struct type is identified uniquely by its name.
Multiple definitions of a struct type are not allowed.

An array type is declared by enclosing the element type in brackets, i.e. ``[`` and ``]``.

There is a ``Null`` type, with a predefined literal named ``null`` of this type.

When declaring fields or variables of reference types, users may suffix the type name with ``?`` to
indicate a ``Nullable`` type. A ``Null`` is an implicit subtype of all ``Nullable`` types.

Examples::

    struct Tree {
        var left: Tree?
        var right: Tree?
    }
    struct Test {
        var intArray: [Int]
    }
    struct TreeArray {
        var array: [Tree?]?
    }

The language does not require forward declarations.

Functions
---------

Users can declare functions, each function must have a unique name.

Functions cannot be overloaded. Functions are not closures.

Functions can accept one or more arguments and may optionally return a result.

The ``func`` keyword introduces a function declaration.

Examples::

    func fib(n: Int)->Int {
        var f1=1
        var f2=1
        var i=n
        while( i>1 ){
            var temp = f1+f2
            f1=f2
            f2=temp
            i=i-1
        }
        return f2
    }

    func foo()->Int {
        return fib(10)
    }

Literals
--------

The only literals are integer values, floating point values, and ``null``.

Variables and Fields
--------------------

The ``var`` keyword is used to introduce a new variable in the current lexical scope,
or to add a field to a struct.

There are two forms of this:

When introducing variables, you can supply an initializer; this removes the need to
specify a type. Examples::

    var i = 1
    var j = foo()

In this form the type of the variable is inferred from the initializer's type.

The second form is more suited when declaring fields in a struct. In this form
a type is required - initializer cannot be set.

Example::

    struct T
    {
        var f: Int
        var arry: [Int]
    }

Creating new instances of Arrays
--------------------------------

The ``new`` keyword is used to create array instances.

It must be followed by an array type name and an initializer.

The array initializer must be a comma separated list of values, enclosed in ``{`` and ``}``.

The array is sized based on number of values in the initializer.

Alternatively the array initializer may have a field named ``len`` that specifies the size of the
array, and a field named ``value`` to specify the value to use.

Examples::

    var arry = new [Int] {1,2,3}
    var arry2 = new [Int] {len=10, value=0}

The second example creates an array with 10 elements and sets the initial value to 0.

Creating new instances of structs
---------------------------------

The ``new`` keyword is used to create struct instances.

It must be followed by the struct type name and an initializer.

The struct initializer must be a comma separated list of field initializers, enclosed in ``{`` and ``}``.

A field initializer has the form of name followed by ``=`` followed by an expression.

Examples::

    var stats = new Stats { age=10, height=100 }


Control Flow
------------

The language is block structured.

A block is enclosed in ``{`` and ``}`` and introduces a lexical scope.

The ``if`` statement allows branching based on a condition. The condition must be an
integer expression; a value of zero is ``false``, any other value is ``true``.

The ``if`` statement can have an optional ``else`` branch.

The only looping construct is the ``while`` statement; this executes the sub statement
as long as the supplied integer condition evaluates to a non zero value.

The ``break`` statement exits a loop.

The ``continue`` statement branches to the beginning of the loop.

The ``return`` statement takes an expression if the function is meant to return a value.
It causes the currently executing function to terminate.

Expressions
-----------

Following table describes the available operators by their precedence (low to high):

+------------+-----------------+----------+
| Operator   | Meaning         | Type     |
|            |                 |          |
+============+=================+==========+
| ``||``     | logical or      | Binary   |
+------------+-----------------+----------+
| ``&&``     | logical and     | Binary   |
+------------+-----------------+----------+
| ``==``     | relational      | Binary   |
| ``!=``     |                 |          |
| ``<``      |                 |          |
| ``<=``     |                 |          |
| ``>``      |                 |          |
| ``>=``     |                 |          |
+------------+-----------------+----------+
| ``+``      | addition        | Binary   |
| ``-``      |                 |          |
+------------+-----------------+----------+
| ``*``      | multiplication  | Binary   |
| ``/``      |                 |          |
| ``%``      |                 |          |
+------------+-----------------+----------+
| ``-``      | negate          | Unary    |
| ``!``      |                 |          |
+------------+-----------------+----------+
| ``(...)``, | function call,  | Postfix  |
| ``[]``,    | array index,    |          |
| ``.`` ID   | field access    |          |
+------------+-----------------+----------+



Floating point operands support the arithmetic operators ``+``, ``-``, ``*``, ``/``,
the relational operators ``==``, ``!=``, ``<``, ``<=``, ``>``, ``>=``, and unary
negation ``-``. The modulo operator ``%``, the logical operators ``&&`` and ``||``, and
the unary ``!`` operator are not supported for floating point operands. A relational
operator applied to floating point operands produces an ``Int`` result (``1`` or ``0``).

There is no implicit conversion between ``Int`` and ``Float``. The two operands of a
binary arithmetic or relational operator must be of the same type; mixing ``Int`` and
``Float`` in a single operation is a type error.

Grammar
-------

The following grammar describes the language syntax::

    program
        : declaration+ EOF
        ;

    declaration
        : structDeclaration
        | functionDeclaration
        ;

    structDeclaration
        : 'struct' IDENTIFIER '{' fields '}'
        ;

    fields
        : varDeclaration+
        ;

    varDeclaration
        : 'var' IDENTIFIER ':' typeName ';'?
        ;

    typeName
        : nominalType
        | arrayType
        ;

    nominalType
        : 'Int'
        | 'Float'
        | IDENTIFIER ('?')?
        ;

    arrayType
        : '[' nominalType ']' ('?')?
        ;

    functionDeclaration
        : 'func' IDENTIFIER '(' parameters? ')' ('->' typeName)? block
        ;

    parameters
        : parameter (',' parameter)*
        ;

    parameter
        : IDENTIFIER ':' typeName
        ;

    block
        : '{' statement* '}'
        ;

    statement
        : 'if' '(' expression ')' statement
        | 'if' '(' expression ')' statement 'else' statement
        | 'while' '(' expression ')' statement
        | postfixExpression '=' expression ';'?
        | block
        | 'break' ';'?
        | 'continue' ';'?
        | varDeclaration
        | 'var' IDENTIFIER '=' expression ';'?
        | 'return' orExpression? ';'?
        | expression ';'?
        ;

    expression
        : orExpression
        ;

    orExpression
        : andExpression ('||' andExpression)*
        ;

    andExpression
        : relationalExpression ('&&' relationalExpression)*
        ;

    relationalExpression
        : additionExpression (('==' | '!='| '>'| '<'| '>='| '<=') additionExpression)*
        ;

    additionExpression
        : multiplicationExpression (('+' | '-') multiplicationExpression)*
        ;

    multiplicationExpression
        : unaryExpression (('*' | '/' | '%') unaryExpression)*
        ;

    unaryExpression
        : ('-' | '!') unaryExpression
        | postfixExpression
        ;

    postfixExpression
        : primaryExpression (indexExpression | callExpression | fieldExpression)*
        ;

    indexExpression
        : '[' orExpression ']'
        ;

    callExpression
        : '(' arguments? ')'
        ;

    arguments
        : orExpression (',' orExpression)*
        ;

    fieldExpression
        : '.' IDENTIFIER
        ;

    primaryExpression
        : INTEGER_LITERAL
        | FLOAT_LITERAL
        | IDENTIFIER
        | '(' orExpression ')'
        | 'new' typeName initExpression
        ;

    initExpression
        : '{' initializers? '}'
        ;

    initializers
        : initializer (',' initializer)*
        ;

    initializer
        : (IDENTIFIER '=')? orExpression
        ;
