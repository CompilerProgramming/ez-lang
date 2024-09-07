grammar EZLanguage;

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
    : simpleType
    | arrayType
    ;

simpleType
    : IDENTIFIER ('?')?
    ;

arrayType
    : '[' simpleType ']' ('?')?
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
    : unaryExpression (('*' | '/' ) unaryExpression)*
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

///////////////////////////////////////////////////////////////////

IDENTIFIER
    :   IDENTIFIER_NONDIGIT
        (   IDENTIFIER_NONDIGIT
        |   DEC_DIGIT
        )*
    ;

fragment
IDENTIFIER_NONDIGIT
    :   NON_DIGIT
    ;

fragment
NON_DIGIT
    :   [a-zA-Z_]
    ;

// number
INTEGER_LITERAL
   : DEC_LITERAL
   ;
DEC_LITERAL: DEC_DIGIT (DEC_DIGIT | '_')*;

fragment DEC_DIGIT: [0-9];


WHITESPACE
    :   [ \t]+
        -> skip
    ;

NEWLINE
    :   (   '\r' '\n'?
        |   '\n'
        )
        -> skip
    ;

LINE_COMMENT
    :   '//' ~[\r\n]*
        -> skip
    ;


ANY: . ;