package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.ListValueType;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static chocopy.common.analysis.types.Type.*;

/**
 * Analyzer that performs ChocoPy type checks on all nodes. Applied after
 * collecting declarations.
 */
public class TypeChecker extends AbstractNodeAnalyzer<Type> {

    /**
     * The current symbol table (changes depending on the function
     * being analyzed).
     */
    private SymbolTable<Type> sym;
    /** Collector for errors. */
    private Errors errors;
    private HashMap<String, String> classTree;

    /**
     * Creates a type checker using GLOBALSYMBOLS for the initial global
     * symbol table and ERRORS0 to receive semantic errors.
     */
    public TypeChecker(SymbolTable<Type> globalSymbols, HashMap<String, String> classTree0, Errors errors0) {
        sym = globalSymbols;
        errors = errors0;
        classTree = classTree0;
    }

    private Type leastUpperBound(Type t1, Type t2) {
        Set<String> seen = new HashSet<>();
        // Traverse through parent of t1 until "object" and store in seen
        String className1 = t1.className();
        String className2 = t2.className();
        while (className1 != null) {
            seen.add(className1);
            className1 = classTree.get(className1);
        }
        while (className2 != null) {
            if (seen.contains(className2)) return new ClassValueType(className2);
            className2 = classTree.get(className2);
        }
        // Fallback to object.
        return Type.OBJECT_TYPE;
    }

    /**
     * Inserts an error message in NODE if there isn't one already.
     * The message is constructed with MESSAGE and ARGS as for
     * String.format.
     */
    private void err(Node node, String message, Object... args) {
        errors.semError(node, message, args);
    }

    @Override
    public Type analyze(Program program) {
        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        for (Stmt stmt : program.statements) {
            stmt.dispatch(this);
        }
        return null;
    }

    //Statements Stmt > If, While, For Expr, Return, Assign


    @Override
    public Type analyze(ExprStmt s) {
        Type t = s.expr.dispatch(this);
        return null;
    }

    @Override
    public Type analyze(AssignStmt s){
        for (Expr e: s.targets){
            Type t = e.dispatch(this);
        }
        s.value.dispatch(this);
        return null;
    }

    @Override
    public Type analyze(ReturnStmt s){
        if(s.value == null){
            return null;
        }
        Type t = s.value.dispatch(this);
        return null;
    }

    @Override 
    public Type analyze(IfStmt s){
        Type cond = s.condition.dispatch(this);
        if(!BOOL_TYPE.equals(cond))
            err(s, "Invalid condition")
        }
        for (Stmt e: s.thenBody){
            Type t = e.dispatch(this);
        }
        for (Stmt e: s.elseBody){
            Type t = e.dispatch(this);
        }
        //todo, typechecking conditions
        return null;
    }

    @Override
    public Type analyze(WhileStmt s) {
        s.condition.dispatch(this);
        for (Stmt i : s.body){
            i.dispatch(this);
        }
        //todo typechecking
        return null;
    }

    @Override
    public Type analyze (ForStmt s){
        //get identifier for s.identifier
        s.iterable.dispatch(this);
        //handle list for s.body
        return null;

    }

    // Literals
    @Override
    public Type analyze(IntegerLiteral i) {
        return i.setInferredType(Type.INT_TYPE);
    }

    @Override
    public Type analyze(BooleanLiteral b) {
        return b.setInferredType(Type.BOOL_TYPE);
    }

    @Override
    public Type analyze(StringLiteral s) {
        return s.setInferredType(Type.STR_TYPE);
    }

    @Override
    public Type analyze(NoneLiteral n) {
        return n.setInferredType(Type.NONE_TYPE);
    }

    // [VAR-READ]
    @Override
    public Type analyze(Identifier id) {
        String varName = id.name;
        Type varType = sym.get(varName);

        if (varType != null && varType.isValueType()) {
            return id.setInferredType(varType);
        }

        err(id, "Not a variable: %s", varName);
        return id.setInferredType(ValueType.OBJECT_TYPE);
    }

    //Not sure how to do this one.
    /**@Override
    public Type analyze(CallExpr f){
        Identifier nameType = f.function;
        Type args = f.args.dispatch(this); //I think this is the correct way to handle typechecking a list.
        
        if(nameType != None && args.isListType()){
            return f.setInferredType(CallExpr);
        }
        err(f, "Not a CallExpr");
        return f.setInferredType(ValueType.OBJECT_TYPE); //not sure if this is correct.
    }**/

    // [VAR-ASSIGN]
    @Override
   public Type analyze(VarDef d){
    Type t = d.var.dispatch(this);
    Type val = d.value.dispatch(this);
    if(t == val){
        return null;
    }
    err(d, "Mismatch type for VarDef.");
    return null;
   }





    // Expressions
    @Override
    public Type analyze(BinaryExpr e) {
        Type t1 = e.left.dispatch(this);
        Type t2 = e.right.dispatch(this);

        switch (e.operator) {
            case "+":
                if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                    return e.setInferredType(INT_TYPE);
                }
                if (STR_TYPE.equals(t1) && STR_TYPE.equals(t2)) {
                    return e.setInferredType(STR_TYPE);
                }
                if (t1 instanceof ListValueType && t2 instanceof ListValueType) {
                    ListValueType lvt1 = (ListValueType)t1;
                    ListValueType lvt2 = (ListValueType)t2;
                    Type t = leastUpperBound(lvt1.elementType, lvt2.elementType);
                    return e.setInferredType(new ListValueType(t));
                }
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                        e.operator, t1, t2);
                return e.setInferredType(INT_TYPE);
            case "-":
            case "*":
            case "//":
            case "%":
                if (!INT_TYPE.equals(t1) || !INT_TYPE.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                            e.operator, t1, t2);
                    return e.setInferredType(INT_TYPE);
                }
                return e.setInferredType(INT_TYPE);
            case "<":
            case "<=":
            case ">=":
            case ">":
                if (!INT_TYPE.equals(t1) || !INT_TYPE.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                            e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            case "==":
            case "!=":
                if ((!INT_TYPE.equals(t1) || !INT_TYPE.equals(t2)) && (!BOOL_TYPE.equals(t1) || !BOOL_TYPE.equals(t2))
                        && (!STR_TYPE.equals(t1) || !STR_TYPE.equals(t2))) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                            e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            case "and":
            case "or":
                if (!BOOL_TYPE.equals(t1) || !BOOL_TYPE.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                            e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            case "is":
                if (INT_TYPE.equals(t1) || INT_TYPE.equals(t2) || BOOL_TYPE.equals(t1) || BOOL_TYPE.equals(t2)
                        || STR_TYPE.equals(t1) || STR_TYPE.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                            e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            default:
                return e.setInferredType(OBJECT_TYPE);
        }

    }

    @Override
    public Type analyze(UnaryExpr e) {
        Type t = e.operand.dispatch(this);
        switch (e.operator) {
            case "not":
                if (!BOOL_TYPE.equals(t)) {
                    err(e, "Cannot apply operator `%s` on type `%s`", e.operator, t);
                }
                return e.setInferredType(BOOL_TYPE);
            case "-":
                if (!INT_TYPE.equals(t)) {
                    err(e, "Cannot apply operator `%s` on type `%s`", e.operator, t);
                }
                return e.setInferredType(INT_TYPE);
            default:
                return e.setInferredType(OBJECT_TYPE);
        }
    }

    @Override 
    public Type analyze(IfExpr e){
        Type t1 = e.condition.dispatch(this);
        Type t2 = e.thenExpr.dispatch(this);
        Type t3 = e.elseExpr.dispatch(this);

        if(NONE_TYPE.equals(t1)){
            err(e, "Can't have empty condition in if expr");
        }
        else if(NONE_TYPE.equals(t2)){
            err(e, "Can't have empty then expression");
        }
        return null;

    }

//Classes
    /**@Override
    public Type analyze(ClassType c){
        String name = c.className;
        
        return c.setInferredType(name_type);
    
    }**/






}
