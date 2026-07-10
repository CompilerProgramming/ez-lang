package com.compilerprogramming.ezlang.compiler;

import com.compilerprogramming.ezlang.compiler.codegen.CodeGen;
import com.compilerprogramming.ezlang.compiler.node.*;
import com.compilerprogramming.ezlang.compiler.print.GraphVisualizer;
import com.compilerprogramming.ezlang.compiler.type.*;
import com.compilerprogramming.ezlang.compiler.util.Ary;
import com.compilerprogramming.ezlang.exceptions.CompilerException;
import com.compilerprogramming.ezlang.lexer.Lexer;
import com.compilerprogramming.ezlang.parser.AST;
import com.compilerprogramming.ezlang.parser.Parser;
import com.compilerprogramming.ezlang.parser.ShortCircuitLowerer;
import com.compilerprogramming.ezlang.semantic.NullableAnalysis;
import com.compilerprogramming.ezlang.semantic.SemaAssignTypes;
import com.compilerprogramming.ezlang.semantic.SemaDefineTypes;
import com.compilerprogramming.ezlang.types.Scope;
import com.compilerprogramming.ezlang.types.Symbol;
import com.compilerprogramming.ezlang.types.EZType;
import com.compilerprogramming.ezlang.types.TypeDictionary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class Compiler {

    public static ConstantNode ZERO; // Very common node, cached here
    public static ConstantNode NIL;  // Very common node, cached here
    public static XCtrlNode XCTRL;   // Very common node, cached here

    TypeDictionary typeDictionary;

    // Compile driver
    public CodeGen _code;

    /**
     * Current ScopeNode - ScopeNodes change as we parse code, but at any point of time
     * there is one current ScopeNode. The reason the current ScopeNode can change is to do with how
     * we handle branching.
     * <p>
     * Each ScopeNode contains a stack of lexical scopes, each scope is a symbol table that binds
     * variable names to Nodes.  The top of this stack represents current scope.
     * <p>
     * We keep a list of all ScopeNodes so that we can show them in graphs
     */
    public ScopeNode _scope;

    /**
     * We clone ScopeNodes when control flows branch; it is useful to have
     * a list of all active ScopeNodes for purposes of visualization of the SoN graph
     */
    public final Stack<ScopeNode> _xScopes = new Stack<>();

    ScopeNode _continueScope;
    ScopeNode _breakScope;      // Merge all the while-breaks here
    FunNode _fun;               // Current function being parsed


    // Mapping from a type name to a Type.  The string name matches
    // `type.str()` call.  No TypeMemPtrs are in here, because Simple does not
    // have C-style '*ptr' references.
    private final HashMap<String, Type> types = new HashMap<>();

    private ArrayList<Node> ctorStack = new ArrayList<>();

    public Compiler(CodeGen codeGen, TypeInteger arg) {
        this._code = codeGen;
    }

    public void parse() {
        this.typeDictionary = createAST(_code._src);
        types.clear();
        populateDefaultTypes(types);
        populateTypes(types);
        ZERO  = con(TypeInteger.ZERO).keep();
        NIL  = con(Type.NIL).keep();
        XCTRL= new XCtrlNode().peephole().keep();
        processFunctions();
    }

    public TypeDictionary createAST(String src) {
        Parser parser = new Parser();
        var program = parser.parse(new Lexer(src));
        analyze(program);
        // Lower boolean && and || to if blocks after nullable analysis has seen the original guards.
        ShortCircuitLowerer.lower(program);
        return bind(program);
    }

    private TypeDictionary analyze(AST.Program program) {
        var typeDict = bind(program);
        NullableAnalysis.analyze(typeDict);
        return typeDict;
    }

    private TypeDictionary bind(AST.Program program) {
        var typeDict = new TypeDictionary();
        var sema = new SemaDefineTypes(typeDict);
        sema.analyze(program);
        var sema2 = new SemaAssignTypes(typeDict);
        sema2.analyze(program);
        return typeDict;
    }


    private void populateDefaultTypes(Map<String, Type> types) {
        // Pre-create primitive and primitive array types
        types.put(typeDictionary.INT.name(), TypeInteger.BOT);
        types.put(typeDictionary.FLOAT.name(), TypeFloat.F64);
        var intArrayType = TypeStruct.makeAry(TypeInteger.U32, _code.getALIAS(), TypeInteger.BOT, _code.getALIAS());
        var ptrIntArrayType = TypeMemPtr.make(intArrayType);
        types.put("[" + typeDictionary.INT.name() + "]", ptrIntArrayType);
        var floatArrayType = TypeStruct.makeAry(TypeInteger.U32, _code.getALIAS(), TypeFloat.F64, _code.getALIAS());
        var ptrFloatArrayType = TypeMemPtr.make(floatArrayType);
        types.put("[" + typeDictionary.FLOAT.name() + "]", ptrFloatArrayType);
        // Also get the types created by default
        for (Type t: Type.gather()) {
            types.put(t.str(), t);
        }
    }

    private void populateTypes(Map<String, Type> structTypes) {
        // First process struct types
        for (var symbol: typeDictionary.getLocalSymbols()) {
            if (symbol instanceof Symbol.TypeSymbol typeSymbol) {
                if (typeSymbol.type instanceof EZType.EZTypeStruct typeStruct) {
                    createSONStructType(structTypes,typeSymbol.name, typeStruct);
                }
                else if (typeSymbol.type instanceof EZType.EZTypeArray typeArray) {
                    getSONType(structTypes, typeArray);
                }
            }
        }
        // Next process function types
        for (var symbol: typeDictionary.getLocalSymbols()) {
            if (symbol instanceof Symbol.FunctionTypeSymbol functionTypeSymbol) {
                createFunctionType(structTypes, functionTypeSymbol);
            }
        }
    }

    private void processFunctions() {
        for (var symbol: typeDictionary.getLocalSymbols()) {
            if (symbol instanceof Symbol.FunctionTypeSymbol functionTypeSymbol) {
                generateFunction(functionTypeSymbol);
            }
        }
    }

    private void createFunctionType(Map<String, Type> structTypes, Symbol.FunctionTypeSymbol functionSymbol) {
        EZType.EZTypeFunction functionType = (EZType.EZTypeFunction) functionSymbol.type;
        Ary<Type> params = new Ary<>(Type.class);
        for (var symbol: functionType.args) {
            if (symbol instanceof Symbol.ParameterSymbol parameterSymbol) {
                Type paramType = getSONType(structTypes, parameterSymbol.type);
                params.push(paramType);
            }
        }
        var retType = getSONType(structTypes, functionType.returnType);
        TypeFunPtr tfp = _code.makeFun2(new TypeTuple(params.asAry()),retType);
        structTypes.put(functionSymbol.name, tfp);
    }

    private void createSONStructType(Map<String, Type> structTypes, String typeName, EZType.EZTypeStruct typeStruct) {
        Ary<Field> fs = new Ary<>(Field.class);
        for (int i = 0; i < typeStruct.numFields(); i++) {
            String name = typeStruct.getFieldName(i);
            EZType type = typeStruct.getField(name); // FIXME
            fs.push(new Field(name,getSONType(structTypes,type),_code.getALIAS(),false));
        }
        // A Struct type may have been created before because of
        // reference from itself; in which case we need to update that
        Type fref = structTypes.get(typeName);
        if (fref != null) {
            if (fref instanceof TypeMemPtr ptr &&
                ptr._obj instanceof TypeStruct ts) {
                assert ts._fields.length == 0;
                // Add the fields to the existing type
                ts._fields = fs.asAry();
            }
            else throw new CompilerException("Expected struct type " + typeName + " but got " + fref);
        }
        else {
            var ts = TypeStruct.make(typeName, fs.asAry());
            var ptr = TypeMemPtr.make((byte)2,ts);
            structTypes.put(typeName,ptr);
        }
    }

    private String getSONTypeName(EZType type) {
        if (type instanceof EZType.EZTypeFunction typeFunction) {
            return typeFunction.name;
        }
        else if (type instanceof EZType.EZTypeArray typeArray) {
            return "*[" + getSONTypeName(typeArray.getElementType()) + "]";
        }
        else if (type instanceof EZType.EZTypeStruct typeStruct) {
            return "*" + typeStruct.name;
        }
        else if (type instanceof EZType.EZTypeInteger ||
                 type instanceof EZType.EZTypeVoid) {
            return TypeInteger.BOT.str();
        }
        else if (type instanceof EZType.EZTypeFloat) {
            return TypeFloat.F64.str();
        }
        else if (type instanceof EZType.EZTypeNull) {
            return TypeNil.NIL.str();
        }
        else if (type instanceof EZType.EZTypeNullable typeNullable) {
            return getSONTypeName(typeNullable.baseType)+"?";
        }
        else throw new CompilerException("Not yet implemented " + type.name());
    }

    private Type getSONType(Map<String, Type> structTypes, EZType type) {
        Type t = structTypes.get(type.name());
        if (t != null) return t;
        if (type instanceof EZType.EZTypeStruct) {
            // For struct types in EeZee language a reference
            // to T means *T in SoN
            // Create SON struct type
            TypeStruct ts = TypeStruct.make(type.name, new Field[0]);
            // Now create *T
            TypeMemPtr ptr = TypeMemPtr.make((byte)2,ts);
            // EeZee T maps to SoN *T
            structTypes.put(type.name(), ptr);
            return ptr;
        }
        else if (type instanceof EZType.EZTypeArray typeArray) {
            // A reference to array in EeZee means
            // *array in SoN
            Type elementType = getSONType(structTypes,typeArray.getElementType());
            if (elementType instanceof TypeMemPtr ptr && ptr.notNull())
                throw new CompilerException("Array element reference types must be nullable", -1);
            TypeStruct ts = TypeStruct.makeArray(TypeInteger.U32, _code.getALIAS(), elementType, _code.getALIAS());
            TypeMemPtr ptr = TypeMemPtr.make((byte)2,ts);
            structTypes.put(typeArray.name(), ptr); // Array type name is not same as ptr str()
            return ptr;
        }
        else if (type instanceof EZType.EZTypeNullable typeNullable) {
            Type baseType = getSONType(structTypes,typeNullable.baseType);
            TypeMemPtr ptr = null;
            if (baseType instanceof TypeMemPtr ptr1) {
                if (ptr1.nullable())
                    ptr = ptr1;
                else
                    ptr = TypeMemPtr.make((byte)3,ptr1._obj);
            }
            else
                ptr = TypeMemPtr.make((byte)2,(TypeStruct) baseType);
            structTypes.put(typeNullable.name(), ptr);
            return ptr;
        }
        else if (type instanceof EZType.EZTypeVoid) {
            return structTypes.get("Int"); // Only allowed in return types
        }
        throw new CompilerException("Count not find type " + type.name());
    }

    private Node ctrl() { return _scope.ctrl(); }
    private <N extends Node> N ctrl(N n) { return _scope.ctrl(n); }

    // TODO because first two slots are MEM and RPC
    private int REGNUM = 2;
    private void defineScopedVars(Scope scope, ScopeNode scopeNode, FunNode fun) {
        for (Symbol symbol: scope.getLocalSymbols()) {
            if (symbol instanceof Symbol.VarSymbol varSymbol) {
                varSymbol.regNumber = REGNUM++;
                Type sonType = types.get(varSymbol.type.name());
                if (sonType == null)
                    throw new CompilerException("Unknown SON Type "+varSymbol.type.name());
                Node init = null;

                if (varSymbol instanceof Symbol.ParameterSymbol) {
                    init = new ParmNode(makeVarName(varSymbol),varSymbol.regNumber,sonType,fun,con(sonType)).peephole();
                }
                scopeNode.define(makeVarName(varSymbol), sonType, false, init);
            }
        }
    }

    private void generateFunction(Symbol.FunctionTypeSymbol functionTypeSymbol) {
        _scope = new ScopeNode();
        _scope.define(ScopeNode.CTRL, Type.CONTROL   , false, null);
        _scope.define(ScopeNode.MEM0, TypeMem.BOT    , false, null);

        ctrl(XCTRL);
        _scope.mem(new MemMergeNode(false));

        var funType = (TypeFunPtr) types.get(functionTypeSymbol.name);
        if (funType == null) throw new CompilerException("Function " + functionTypeSymbol.name + " not found");

        // Parse whole program, as-if function header "{ int arg -> body }"
        generateFunctionBody(functionTypeSymbol,funType);
        _code.link(funType)._name = functionTypeSymbol.name;

        // Clean up and reset
        //_xScopes.pop();
        _scope.kill();
//        for( StructNode init : INITS.values() )
//            init.unkeep().kill();
//        INITS.clear();
        _code._stop.peephole();
//        if( show ) showGraph();
        //showGraph();
    }

    /**
     * Dumps out the node graph
     * @return {@code null}
     */
    Node showGraph() {
        System.out.println(new GraphVisualizer().generateDotOutput(_code._stop,_scope,_xScopes));
        return null;
    }

    /**
     *  Parses a function body, assuming the header is parsed.
     */
    private ReturnNode generateFunctionBody(Symbol.FunctionTypeSymbol functionTypeSymbol, TypeFunPtr sig) {
        // Stack parser state on the local Java stack, and unstack it later
        Node oldctrl = ctrl().keep();
        Node oldmem  = _scope.mem().keep();
        FunNode oldfun  = _fun;
        ScopeNode breakScope = _breakScope; _breakScope = null;
        ScopeNode continueScope = _continueScope; _continueScope = null;

        FunNode fun = _fun = (FunNode)peep(new FunNode(sig, functionTypeSymbol.name,null,_code._start));
        // Once the function header is available, install in linker table -
        // allowing recursive functions.  Linker matches on declared args and
        // exact fidx, and ignores the return (because the fidx will only match
        // the exact single function).
        _code.link(fun);

        Node rpc = new ParmNode("$rpc",0, TypeRPC.BOT,fun,con(TypeRPC.BOT)).peephole();

        // Build a multi-exit return point for all function returns
        RegionNode r = new RegionNode(null,null).init();
        assert r.inProgress();
        PhiNode rmem = new PhiNode(ScopeNode.MEM0, TypeMem.BOT,r,null).init();
        PhiNode rrez = new PhiNode(ScopeNode.ARG0, Type.BOTTOM,r,null).init();
        ReturnNode ret = new ReturnNode(r, rmem, rrez, rpc, fun).init();
        fun.setRet(ret);
        assert ret.inProgress();
        _code._stop.addDef(ret);

        // Pre-call the function from Start, with worse-case arguments.  This
        // represents all the future, yet-to-be-parsed functions calls and
        // external calls.
        _scope.push(ScopeNode.Kind.Function);
        ctrl(fun);              // Scope control from function
        // Private mem alias tracking per function
        MemMergeNode mem = new MemMergeNode(true);
        mem.addDef(null);       // Alias#0
        mem.addDef(new ParmNode(ScopeNode.MEM0,1, TypeMem.BOT,fun,con(TypeMem.BOT)).peephole()); // All aliases
        _scope.mem(mem);
        // All args, "as-if" called externally
        AST.FuncDecl funcDecl = (AST.FuncDecl) functionTypeSymbol.functionDecl;
        REGNUM = 2;
        defineScopedVars(funcDecl.scope,_scope,fun);

        // Parse the body
        Node last = compileStatement(funcDecl.block);

        // Last expression is the return
        if( ctrl()._type== Type.CONTROL )
            fun.addReturn(ctrl(), _scope.mem().merge(), last);

        // Pop off the inProgress node on the multi-exit Region merge
        assert r.inProgress();
        r   ._inputs.pop();
        rmem._inputs.pop();
        rrez._inputs.pop();
        assert !r.inProgress();

        // Force peeps, which have been avoided due to inProgress
        ret.setDef(1,rmem.peephole());
        ret.setDef(2,rrez.peephole());
        ret.setDef(0,r.peephole());
        ret = (ReturnNode)ret.peephole();

        // Function scope ends
        _scope.pop();
        _fun = oldfun;
        _breakScope = breakScope;
        _continueScope = continueScope;
        // Reset control and memory to pre-function parsing days
        ctrl(oldctrl.unkeep());
        _scope.mem(oldmem.unkeep());

        return ret;
    }

    private Node compileExpr(AST.Expr expr) {
        switch (expr) {
            case AST.LiteralExpr constantExpr -> {
                return compileConstantExpr(constantExpr);
            }
            case AST.BinaryExpr binaryExpr -> {
                return compileBinaryExpr(binaryExpr);
            }
            case AST.UnaryExpr unaryExpr -> {
                return compileUnaryExpr(unaryExpr);
            }
            case AST.NameExpr symbolExpr -> {
                return compileSymbolExpr(symbolExpr);
            }
            case AST.NewExpr newExpr -> {
                return compileNewExpr(newExpr);
            }
            case AST.InitExpr initExpr -> {
                return compileInitExpr(initExpr);
            }
            case AST.ArrayLoadExpr arrayLoadExpr -> {
                return compileArrayIndexExpr(arrayLoadExpr);
            }
            case AST.ArrayStoreExpr arrayStoreExpr -> {
                return compileArrayStoreExpr(arrayStoreExpr);
            }
            case AST.GetFieldExpr getFieldExpr -> {
                return compileFieldExpr(getFieldExpr);
            }
            case AST.SetFieldExpr setFieldExpr -> {
                return compileSetFieldExpr(setFieldExpr);
            }
            case AST.CallExpr callExpr -> {
                return compileCallExpr(callExpr);
            }
            default -> throw new IllegalStateException("Unexpected value: " + expr);
        }
    }

    private Node compileFieldExpr(AST.GetFieldExpr getFieldExpr) {
        Node objPtr = compileExpr(getFieldExpr.object).keep();
        // Sanity check expr for being a reference
        if( !(objPtr._type instanceof TypeMemPtr ptr) ) {
            throw new CompilerException("Unexpected type " + objPtr._type.str(), getFieldExpr.lineNumber);
        }
        String name = getFieldExpr.fieldName;
        TypeStruct base = ptr._obj;
        int fidx = base.find(name);
        if( fidx == -1 ) throw error("Accessing unknown field '" + name + "' from '" + ptr.str() + "'", getFieldExpr.lineNumber);
        Field f = base._fields[fidx];  // Field from field index
        Type tf = f._type;
        // Field offset; fixed for structs
        Node off = con(base.offset(fidx)).keep();
        Node load = new LoadNode(name, f._alias, tf, memAlias(f._alias), objPtr, off);
        // Arrays include control, as a proxy for a safety range check
        // Structs don't need this; they only need a NPE check which is
        // done via the type system.
        load = peep(load);
        objPtr.unkeep();
        off.unkeep();
        return load;
    }

    private Node compileSetFieldExpr(AST.SetFieldExpr setFieldExpr) {
        Node objPtr;
        if (setFieldExpr instanceof AST.InitFieldExpr)
            objPtr = ctorStack.getLast();
        else
            objPtr = compileExpr(setFieldExpr.object);
        // Sanity check expr for being a reference
        if( !(objPtr._type instanceof TypeMemPtr ptr) ) {
            throw new CompilerException("Unexpected type " + objPtr._type.str(), setFieldExpr.lineNumber);
        }
        Node val = compileExpr(setFieldExpr.value).keep();
        String name = setFieldExpr.fieldName;
        TypeStruct base = ptr._obj;
        int fidx = base.find(name);
        if( fidx == -1 ) throw error("Accessing unknown field '" + name + "' from '" + ptr.str() + "'", setFieldExpr.lineNumber);
        Field f = base._fields[fidx];  // Field from field index
        Type tf = f._type;
        Node mem = memAlias(f._alias);
        Node st = new StoreNode(f._fname,f._alias,tf,mem,objPtr,con(base.offset(fidx)),val.unkeep(),true).peephole();
        memAlias(f._alias,st);
        return objPtr;
    }

    // # operator
    private Node compileArrayLengthExpr(AST.Expr arrayExpr, int lineNumber) {
        Node objPtr = compileExpr(arrayExpr).keep();
        if( !(objPtr._type instanceof TypeMemPtr ptr) ) {
            throw new CompilerException("Unexpected type " + objPtr._type.str(), lineNumber);
        }
        String name = "#";
        TypeStruct base = ptr._obj;
        int fidx = base.find(name);
        if( fidx == -1 ) throw error("Accessing unknown field '" + name + "' from '" + ptr.str() + "'", lineNumber);
        Field f = base._fields[fidx];
        Node off = con(base.offset(fidx)).keep();
        Node load = new LoadNode(name, f._alias, f._type, memAlias(f._alias), objPtr, off);
        load = peep(load);
        objPtr.unkeep();
        off.unkeep();
        return load;
    }

    private Node compileArrayIndexExpr(AST.ArrayLoadExpr arrayLoadExpr) {
        Node objPtr = compileExpr(arrayLoadExpr.array).keep();
        // Sanity check expr for being a reference
        if( !(objPtr._type instanceof TypeMemPtr ptr) ) {
            throw new CompilerException("Unexpected type " + objPtr._type.str(), arrayLoadExpr.lineNumber);
        }
        String name = "[]";
        TypeStruct base = ptr._obj;
        Node index = compileExpr(arrayLoadExpr.expr).keep();
        Node off = peep(new AddNode(con(base.aryBase()),peep(new ShlNode(index.unkeep(),con(base.aryScale()))))).keep();
        int fidx = base.find(name);
        if( fidx == -1 ) throw error("Accessing unknown field '" + name + "' from '" + ptr.str() + "'", arrayLoadExpr.lineNumber);
        Field f = base._fields[fidx];  // Field from field index
        Type tf = f._type;
        Node load = new LoadNode(name, f._alias, tf, memAlias(f._alias), objPtr, off);
        // Arrays include control, as a proxy for a safety range check
        // Structs don't need this; they only need a NPE check which is
        // done via the type system.
        load.setDef(0,ctrl());
        load = peep(load);
        objPtr.unkeep();
        off.unkeep();
        return load;
    }

    private Node compileArrayStoreExpr(AST.ArrayStoreExpr arrayStoreExpr) {
        Node objPtr;
        if (arrayStoreExpr instanceof AST.ArrayInitExpr)
            objPtr = ctorStack.getLast();
        else
            objPtr = compileExpr(arrayStoreExpr.array);
        // Sanity check expr for being a reference
        if( !(objPtr._type instanceof TypeMemPtr ptr) ) {
            throw new CompilerException("Unexpected type " + objPtr._type.str(), arrayStoreExpr.lineNumber);
        }
        String name = "[]";
        TypeStruct base = ptr._obj;
        Node index = compileExpr(arrayStoreExpr.expr).keep();
        Node off = peep(new AddNode(con(base.aryBase()),peep(new ShlNode(index.unkeep(),con(base.aryScale()))))).keep();
        Node val = compileExpr(arrayStoreExpr.value).keep();
        int fidx = base.find(name);
        if( fidx == -1 ) throw error("Accessing unknown field '" + name + "' from '" + ptr.str() + "'", arrayStoreExpr.lineNumber);
        Field f = base._fields[fidx];  // Field from field index
        Type tf = f._type;
        Node mem = memAlias(f._alias);
        Node st = new StoreNode(f._fname,f._alias,tf,mem,objPtr,off.unkeep(),val.unkeep(),true).peephole();
        // Arrays include control, as a proxy for a safety range check
        // Structs don't need this; they only need a NPE check which is
        // done via the type system.
        st.setDef(0,ctrl());
        memAlias(f._alias,st);
        return objPtr;
    }

    private Node compileInitExpr(AST.InitExpr initExpr) {
        Node objPtr = compileExpr(initExpr.newExpr).keep();
        ctorStack.add(objPtr);
        // Sanity check expr for being a reference
        if( !(objPtr._type instanceof TypeMemPtr ptr) ) {
            throw new CompilerException("Unexpected type " + objPtr._type.str(), initExpr.lineNumber);
        }
        if (initExpr.initExprList != null && !initExpr.initExprList.isEmpty()) {
            for (AST.Expr expr : initExpr.initExprList) {
                compileExpr(expr);
            }
        }
        ctorStack.removeLast();
        return objPtr.unkeep();
    }

    private Node newArray(TypeStruct ary, Node arrayLen) {
        int base = ary.aryBase ();
        int scale = ary.aryScale();
        // array size = base + len << scale
        Node size = peep(new AddNode(con(base),peep(new ShlNode(arrayLen.keep(),con(scale)))));
        Node ptr = newStruct(ary,size).keep();
        // arrays are structs with two fields
        // first field is length
        // second field is [] data
        Field[] fs = ary._fields;
        // store length as int32
        Node mem = memAlias(fs[0]._alias);
        Node st = new StoreNode(fs[0]._fname,fs[0]._alias,fs[0]._type,mem,ptr,con(ary.offset(0)),arrayLen.unkeep(),true).peephole();
        memAlias(fs[0]._alias,st);
        return ptr.unkeep();
    }

    /**
     * Return a NewNode initialized memory.
     * @param obj is the declared type, with GLB fields
     */
    private Node newStruct(TypeStruct obj, Node size) {
        Field[] fs = obj._fields;
        if( fs==null )
            throw error("Unknown struct type '" + obj._name + "'");
        int len = fs.length;
        Node[] ns = new Node[2+len];
        ns[0] = ctrl();         // Control in slot 0
        // Total allocated length in bytes
        ns[1] = size;
        // Memory aliases for every field
        for( int i = 0; i < len; i++ )
            ns[2+i] = memAlias(fs[i]._alias);
        // FIXME make
        Node nnn = new NewNode(TypeMemPtr.make(obj), ns).peephole().keep();
        for( int i = 0; i < len; i++ )
            memAlias(fs[i]._alias, new ProjNode(nnn,i+2,memName(fs[i]._alias)).peephole());
        return new ProjNode(nnn.unkeep(),1,obj._name).peephole();
    }

    private Node compileNewExpr(AST.NewExpr newExpr) {
        EZType type = newExpr.type;
        if (type instanceof EZType.EZTypeArray typeArray) {
            TypeMemPtr tarray = (TypeMemPtr) types.get(typeArray.name());
            return newArray(tarray._obj,newExpr.len==null?ZERO:compileExpr(newExpr.len));
        }
        else if (type instanceof EZType.EZTypeStruct typeStruct) {
            TypeMemPtr tptr = (TypeMemPtr) types.get(typeStruct.name());
            return newStruct(tptr._obj,con(tptr._obj.offset(tptr._obj._fields.length)));
        }
        else
            throw new CompilerException("Unexpected type: " + type, newExpr.lineNumber);
    }


    private Node compileUnaryExpr(AST.UnaryExpr unaryExpr) {
        String opCode = unaryExpr.op.str;
        switch (opCode) {
            case "-": {
                Node expr = compileExpr(unaryExpr.expr);
                return unaryExpr.type instanceof EZType.EZTypeFloat
                    ? peep(new MinusFNode(expr))
                    : peep(new MinusNode(expr).widen());
            }
            // Maybe below we should explicitly set Int
            case "!": return peep(new NotNode(compileExpr(unaryExpr.expr)));
            case "#": return compileArrayLengthExpr(unaryExpr.expr, unaryExpr.lineNumber);
            default: throw new CompilerException("Invalid unary op", unaryExpr.lineNumber);
        }
    }

    private Node compileBinaryExpr(AST.BinaryExpr binaryExpr) {
        String opCode = binaryExpr.op.str;
        int idx=0;
        boolean negate = false;
        var lhs = compileExpr(binaryExpr.expr1);
        switch (opCode) {
            case "&&":
            case "||":
                // We should never reach here because the AST is lowered such that these ops
                // have been replaced by standard if blocks.
                throw new CompilerException("Not yet implemented", binaryExpr.lineNumber);
            case "==":
                idx=2;  lhs = binaryExpr.expr1.type instanceof EZType.EZTypeFloat ? new BoolNode.EQF(lhs, null) : new BoolNode.EQ(lhs, null);
                break;
            case "!=":
                idx=2;  lhs = binaryExpr.expr1.type instanceof EZType.EZTypeFloat ? new BoolNode.EQF(lhs, null) : new BoolNode.EQ(lhs, null); negate=true;
                break;
            case "<=":
                idx=2;  lhs = binaryExpr.expr1.type instanceof EZType.EZTypeFloat ? new BoolNode.LEF(lhs, null) : new BoolNode.LE(lhs, null);
                break;
            case "<":
                idx=2;  lhs = binaryExpr.expr1.type instanceof EZType.EZTypeFloat ? new BoolNode.LTF(lhs, null) : new BoolNode.LT(lhs, null);
                break;
            case ">=":
                idx=1;  lhs = binaryExpr.expr1.type instanceof EZType.EZTypeFloat ? new BoolNode.LEF(null, lhs) : new BoolNode.LE(null, lhs);
                break;
            case ">":
                idx=1;  lhs = binaryExpr.expr1.type instanceof EZType.EZTypeFloat ? new BoolNode.LTF(null, lhs) : new BoolNode.LT(null, lhs);
                break;
            case "+":
                idx=2; lhs = binaryExpr.type instanceof EZType.EZTypeFloat ? new AddFNode(lhs,null) : new AddNode(lhs,null);
                break;
            case "-":
                idx=2; lhs = binaryExpr.type instanceof EZType.EZTypeFloat ? new SubFNode(lhs,null) : new SubNode(lhs,null);
                break;
            case "*":
                idx=2; lhs = binaryExpr.type instanceof EZType.EZTypeFloat ? new MulFNode(lhs,null) : new MulNode(lhs,null);
                break;
            case "/":
                idx=2; lhs = binaryExpr.type instanceof EZType.EZTypeFloat ? new DivFNode(lhs,null) : new DivNode(lhs,null);
                break;
            default:
                throw new CompilerException("Not yet implemented", binaryExpr.lineNumber);
        }
        lhs.setDef(idx,compileExpr(binaryExpr.expr2));
        lhs = peep(lhs.widen());
        if( negate )        // Extra negate for !=
            lhs = peep(new NotNode(lhs));
        return lhs;
    }

    private Node compileConstantExpr(AST.LiteralExpr constantExpr) {
        if (constantExpr.type instanceof EZType.EZTypeInteger)
            return con(constantExpr.value.num.intValue());
        else if (constantExpr.type instanceof EZType.EZTypeFloat)
            return con(TypeFloat.constant(constantExpr.value.num.doubleValue()));
        else if (constantExpr.type instanceof EZType.EZTypeNull)
            return NIL;
        else throw new CompilerException("Invalid constant type", constantExpr.lineNumber);
    }

    private Node compileSymbolExpr(AST.NameExpr symbolExpr) {
        if (symbolExpr.type instanceof EZType.EZTypeFunction functionType)
            return con(types.get(functionType.name));
        else {
            Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) symbolExpr.symbol;
            Var v = _scope.lookup(makeVarName(varSymbol));
            if (v == null) throw new CompilerException("Unknown variable name: " + varSymbol.name, symbolExpr.lineNumber);
            Node n = _scope.in(v._idx);
            if (n == null) throw new CompilerException("Variable " + varSymbol.name + " not defined", symbolExpr.lineNumber);
            return n;
        }
    }

    /**
     *  Parse function call arguments; caller will parse the surrounding "()"
     * <pre>
     *   ( arg* )
     * </pre>
     */
    private Node compileCallExpr(AST.CallExpr callExpr) {
        Node expr = compileExpr(callExpr.callee);
        if( expr._type == Type.NIL )
            throw error("Calling a null function pointer", callExpr.lineNumber);
        if( !(expr instanceof FRefNode) && !expr._type.isa(TypeFunPtr.BOT) )
            throw error("Expected a function but got "+expr._type.glb(false).str(), callExpr.lineNumber);
        expr.keep();            // Keep while parsing args

        Ary<Node> args = new Ary<Node>(Node.class);
        args.push(null);        // Space for ctrl,mem
        args.push(null);
        for (AST.Expr e: callExpr.args) {
            Node arg = compileExpr(e);
            args.push(arg.keep());
        }
        // Control & memory after parsing args
        args.set(0,ctrl().keep());
        args.set(1,_scope.mem().merge().keep());
        args.push(expr);        // Function pointer
        // Unkeep them all
        for( Node arg : args )
            arg.unkeep();
        // Dead into the call?  Skip all the node gen
        if( ctrl()._type == Type.XCONTROL ) {
            for( Node arg : args )
                if( arg.isUnused() )
                    arg.kill();
            return con(Type.TOP);
        }

        // Into the call
        CallNode call = (CallNode)new CallNode(args.asAry()).peephole();

        // Post-call setup
        CallEndNode cend = (CallEndNode)new CallEndNode(call).peephole();
        call.peephole();        // Rerun peeps after CallEnd, allows early inlining
        // Control from CallEnd
        ctrl(new CProjNode(cend,0,ScopeNode.CTRL).peephole());
        // Memory from CallEnd
        MemMergeNode mem = new MemMergeNode(true);
        mem.addDef(null);       // Alias#0
        mem.addDef(new ProjNode(cend,1,ScopeNode.MEM0).peephole());
        _scope.mem(mem);
        // Call result
        return new ProjNode(cend,2,"#2").peephole();
    }

    private String makeVarName(Symbol.VarSymbol varSymbol) {
        return varSymbol.name; // + "$" + varSymbol.regNumber;
    }

    private Node compileStatement(AST.Stmt statement) {
        switch (statement) {
            case AST.BlockStmt blockStmt -> {
                return compileBlock(blockStmt);
            }
            case AST.VarStmt letStmt -> {
                return compileLet(letStmt);
            }
            case AST.VarDeclStmt varDeclStmt -> {
                return ZERO;
            }
            case AST.IfElseStmt ifElseStmt -> {
                return compileIf(ifElseStmt);
            }
            case AST.WhileStmt whileStmt -> {
                return compileWhile(whileStmt);
            }
            case AST.ContinueStmt continueStmt -> {
                return compileContinue(continueStmt);
            }
            case AST.BreakStmt breakStmt -> {
                return compileBreak(breakStmt);
            }
            case AST.ReturnStmt returnStmt -> {
                return compileReturn(returnStmt);
            }
            case AST.AssignStmt assignStmt -> {
                return compileAssign(assignStmt);
            }
            case AST.ExprStmt exprStmt -> {
                return compileExprStmt(exprStmt);
            }
            default -> throw new IllegalStateException("Unexpected value: " + statement);
        }
    }

    private ScopeNode jumpTo(ScopeNode toScope) {
        ScopeNode cur = _scope.dup();
        ctrl(XCTRL); // Kill current scope
        // Prune nested lexical scopes that have depth > than the loop head
        // We use _breakScope as a proxy for the loop head scope to obtain the depth
        while( cur._lexSize.size() > _breakScope._lexSize.size() )
            cur.pop();
        // If this is a continue then first time the target is null
        // So we just use the pruned current scope as the base for the
        // "continue"
        if( toScope == null )
            return cur;
        // toScope is either the break scope, or a scope that was created here
        assert toScope._lexSize.size() <= _breakScope._lexSize.size();
        toScope.ctrl(toScope.mergeScopes(cur).peephole());
        return toScope;
    }
    private void checkLoopActive() { if (_breakScope == null) throw error("No active loop for a break or continue"); }

    private Node compileBreak(AST.BreakStmt breakStmt) {
        checkLoopActive();
        // At the time of the break, and loop-exit conditions are only valid if
        // they are ALSO valid at the break.  It is the intersection of
        // conditions here, not the union.
        _breakScope.removeGuards(_breakScope.ctrl());
        _breakScope = jumpTo(_breakScope );
        _breakScope.addGuards(_breakScope.ctrl(), null, false);
        return ZERO;
    }

    private Node compileContinue(AST.ContinueStmt continueStmt) {
        checkLoopActive(); _continueScope = jumpTo( _continueScope ); return ZERO;
    }

    private Node compileWhile(AST.WhileStmt whileStmt) {
        var savedContinueScope = _continueScope;
        var savedBreakScope    = _breakScope;

        // Loop region has two control inputs, the first is the entry
        // point, and second is back edge that is set after loop is parsed
        // (see end_loop() call below).  Note that the absence of back edge is
        // used as an indicator to switch off peepholes of the region and
        // associated phis; see {@code inProgress()}.

        ctrl(new LoopNode(ctrl()).peephole()); // Note we set back edge to null here

        // At loop head, we clone the current Scope (this includes all
        // names in every nesting level within the Scope).
        // We create phis eagerly for all the names we find, see dup().

        // Save the current scope as the loop head
        ScopeNode head = _scope.keep();
        // Clone the head Scope to create a new Scope for the body.
        // Create phis eagerly as part of cloning
        _xScopes.push(_scope = _scope.dup(true)); // The true argument triggers creating phis

        // Parse predicate
        checkConditionType(whileStmt.condition.type, whileStmt.lineNumber);
        var pred = compileExpr(whileStmt.condition);

        // IfNode takes current control and predicate
        Node ifNode = new IfNode(ctrl(), pred.keep()).peephole();
        // Setup projection nodes
        Node ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole().keep();
        Node ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole();

        // Clone the body Scope to create the break/exit Scope which accounts for any
        // side effects in the predicate.  The break/exit Scope will be the final
        // scope after the loop, and its control input is the False branch of
        // the loop predicate.  Note that body Scope is still our current scope.
        ctrl(ifF);
        _xScopes.push(_breakScope = _scope.dup());
        _breakScope.addGuards(ifF,pred,true); // Up-cast predicate

        // No continues yet
        _continueScope = null;

        // Parse the true side, which corresponds to loop body
        // Our current scope is the body Scope
        ctrl(ifT.unkeep());     // set ctrl token to ifTrue projection
        _scope.addGuards(ifT,pred.unkeep(),false); // Up-cast predicate
        compileStatement(whileStmt.stmt);       // Parse loop body
        _scope.removeGuards(ifT);

        // Merge the loop bottom into other continue statements
        if (_continueScope != null) {
            _continueScope = jumpTo(_continueScope);
            _scope.kill();
            _scope = _continueScope;
        }


        // The true branch loops back, so whatever is current _scope.ctrl gets
        // added to head loop as input.  endLoop() updates the head scope, and
        // goes through all the phis that were created earlier.  For each phi,
        // it sets the second input to the corresponding input from the back
        // edge.  If the phi is redundant, it is replaced by its sole input.
        var exit = _breakScope;
        head.endLoop(_scope, exit);
        head.unkeep().kill();

        _xScopes.pop();       // Cleanup
        _xScopes.pop();       // Cleanup

        _continueScope = savedContinueScope;
        _breakScope = savedBreakScope;

        // At exit the false control is the current control, and
        // the scope is the exit scope after the exit test.
        _xScopes.push(exit);
        _scope = exit;
        return ZERO;
    }

    private void checkConditionType(EZType conditionType, int lineNumber) {
        if (!(conditionType instanceof EZType.EZTypeInteger))
            throw new CompilerException("Condition expression must be Int type", lineNumber);
    }

    private Node compileIf(AST.IfElseStmt ifElseStmt) {
        checkConditionType(ifElseStmt.condition.type, ifElseStmt.lineNumber);
        var pred = compileExpr(ifElseStmt.condition).keep();
        // IfNode takes current control and predicate
        Node ifNode = new IfNode(ctrl(), pred).peephole();
        // Setup projection nodes
        Node ifT = new CProjNode(ifNode.  keep(), 0, "True" ).peephole().keep();
        Node ifF = new CProjNode(ifNode.unkeep(), 1, "False").peephole().keep();
        // In if true branch, the ifT proj node becomes the ctrl
        // But first clone the scope and set it as current
        ScopeNode fScope = _scope.dup(); // Duplicate current scope
        _xScopes.push(fScope); // For graph visualization we need all scopes

        // Parse the true side
        ctrl(ifT.unkeep());     // set ctrl token to ifTrue projection
        _scope.addGuards(ifT,pred,false); // Up-cast predicate
        Node lhs = compileStatement(ifElseStmt.ifStmt).keep(); // Parse true-side
        _scope.removeGuards(ifT);

        // See if a one-sided def was made: "if(pred) int x = 1;" and throw.
        // See if any forward-refs were made, and copy them to the other side:
        // "pred ? n*fact(n-1) : 1"
        fScope.balanceIf(_scope);

        ScopeNode tScope = _scope;

        // Parse the false side
        _scope = fScope;        // Restore scope, then parse else block if any
        ctrl(ifF.unkeep());     // Ctrl token is now set to ifFalse projection
        // Up-cast predicate, even if not else clause, because predicate can
        // remain true if the true clause exits: `if( !ptr ) return 0; return ptr.fld;`
        _scope.addGuards(ifF,pred,true);
        boolean doRHS = ifElseStmt.elseStmt != null;
        Node rhs = (doRHS
            ? compileStatement(ifElseStmt.elseStmt)
            : con(lhs._type.makeZero())).keep();
        _scope.removeGuards(ifF);
        if( doRHS )
            fScope = _scope;
        pred.unkeep();

        _scope = tScope;
        _xScopes.pop();       // Discard pushed from graph display
        // See if a one-sided def was made: "if(pred) int x = 1;" and throw.
        // See if any forward-refs were made, and copy them to the other side:
        // "pred ? n*fact(n-1) : 1"
        tScope.balanceIf(fScope);

        // Merge results
        RegionNode r = ctrl(tScope.mergeScopes(fScope));
        Node ret = peep(new PhiNode("",lhs._type.meet(rhs._type),r,lhs.unkeep(),rhs.unkeep()));
        r.peephole();
        return ret;
    }

    private Node compileExprStmt(AST.ExprStmt exprStmt) {
        return compileExpr(exprStmt.expr);
    }

    private Node compileAssign(AST.AssignStmt assignStmt) {
        var nameExpr = assignStmt.nameExpr;
        String name = nameExpr.name;
        if (!(nameExpr.symbol instanceof Symbol.VarSymbol varSymbol))
            throw new CompilerException("Assignment requires a variable name", assignStmt.lineNumber);
        String scopedName = makeVarName(varSymbol);
        // Parse rhs
        Node expr = compileExpr(assignStmt.rhs);
        Var def = _scope.lookup(scopedName);
        if( def==null )
            throw error("Undefined name '" + name + "'", assignStmt.lineNumber);
        // Update
        _scope.update(scopedName,expr);
        return expr;
    }

    private Node compileLet(AST.VarStmt letStmt) {
        String name = letStmt.varName;
        String scopedName = makeVarName(letStmt.symbol);
        // Parse rhs
        Node expr = compileExpr(letStmt.expr);
        Var def = _scope.lookup(scopedName);
        if( def==null )
            throw error("Undefined name '" + name + "'", letStmt.lineNumber);
        // Update
        _scope.update(scopedName,expr);
        return expr;
    }

    private Node compileReturn(AST.ReturnStmt returnStmt) {
        Node expr;
        if (returnStmt.expr != null)
            expr = compileExpr(returnStmt.expr);
        else
            expr = ZERO;
        // Need default memory, since it can be lazy, need to force
        // a non-lazy Phi
        _fun.addReturn(ctrl(), _scope.mem().merge(), expr);
        ctrl(XCTRL);            // Kill control
        return expr;
    }

    private Node compileBlock(AST.BlockStmt block) {
        Node last = ZERO;
        _scope.push(ScopeNode.Kind.Block);
        defineScopedVars(block.scope, _scope, _fun);
        for (AST.Stmt stmt: block.stmtList) {
            last = compileStatement(stmt);
        }
        _scope.pop();
        return last;
    }

    public static Node con(long con ) { return con==0 ? ZERO : con(TypeInteger.constant(con));  }
    public static ConstantNode con( Type t ) { return (ConstantNode)new ConstantNode(t).peephole();  }
    public Node peep( Node n ) {
        // Peephole, then improve with lexically scoped guards
        return _scope.upcastGuard(n.peephole());
    }

    // We set up memory aliases by inserting special vars in the scope these
    // variables are prefixed by $ so they cannot be referenced in Simple code.
    // Using vars has the benefit that all the existing machinery of scoping
    // and phis work as expected
    private Node memAlias(int alias         ) { return _scope.mem(alias    ); }
    private void memAlias(int alias, Node st) {        _scope.mem(alias, st); }
    public static String memName(int alias) { return ("$"+alias).intern(); }

    CompilerException errorSyntax(String syntax, int lineNumber) { return _errorSyntax("expected "+syntax, lineNumber);  }
    private CompilerException _errorSyntax(String msg, int lineNumber) {
        return error("Syntax error, "+msg, lineNumber);
    }
    public static CompilerException error(String msg, int lineNumber) { return new CompilerException(msg, lineNumber); }
    public static CompilerException error(String msg) { return new CompilerException(msg, -1); }
}
