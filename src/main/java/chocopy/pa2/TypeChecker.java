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
    /**
     * Collector for errors.
     */
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
        if (t1 == null || t2 == null) return OBJECT_TYPE;
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

    // Checks if t1 is a subclass of t2
    private boolean isSubclass(Type t1, Type t2) {
        if (t1 == null || t2 == null) return false;
        if (t1.equals(t2)) {
            return true;
        }

        if (t1 instanceof ListValueType && t2.equals(OBJECT_TYPE)) {
            return true;
        }

        String className1 = t1.className();
        String className2 = t2.className();
        while (className1 != null) {
            if (className1.equals(className2)) return true;
            className1 = classTree.get(className1);
        }
        return false;
    }

    // t1 <=_a t2 operator
    private boolean leqA(Type t1, Type t2) {
        if (t1 == null || t2 == null) return false;
        // t1 <= t2
        if (isSubclass(t1, t2)) return true;
        // t1 is None, t2 is not int, str, or bool
        if (t1.equals(NONE_TYPE) && (!t2.isSpecialType())) return true;
        // t1 is Empty, t2 is [T]
        if (t1.equals(EMPTY_TYPE) && t2 instanceof ListValueType) return true;
        // t1 is [None], t2 is [T], None <=_a T
        if (t1 instanceof ListValueType && t2 instanceof ListValueType) {
            ListValueType lvt1 = (ListValueType) t1;
            ListValueType lvt2 = (ListValueType) t2;
            if (lvt1.elementType.equals(NONE_TYPE) && leqA(NONE_TYPE, lvt2.elementType)) {
                return true;
            }
        }
        return false;
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
        s.expr.dispatch(this);
        return null;
    }

    @Override
    public Type analyze(AssignStmt s) {
        for (Expr target : s.targets) {
            target.dispatch(this);
            // Check if target can be assigned to.
            if (!(target instanceof Identifier || target instanceof IndexExpr || target instanceof MemberExpr)) {
                err(target, "Cannot assign to %s", target);
            }
        }
        Type t1 = s.value.dispatch(this);
        for (Expr target : s.targets) {
            Type t = target.getInferredType();
            if (t != null && !leqA(t1, t)) {
                err(s, "Expected type `%s`; got type `%s`", t1, t);
            }
        }
        return null;
    }

    @Override
    public Type analyze(ReturnStmt s) {
        if (s.value == null) {
            return null;
        }
        return null;
    }

    @Override
    public Type analyze(IfStmt s) {
        Type cond = s.condition.dispatch(this);
        if (!BOOL_TYPE.equals(cond)) {
            err(s, "Condition expression must be of type bool.");
        }
        for (Stmt e : s.thenBody) {
            e.dispatch(this);
        }
        for (Stmt e : s.elseBody) {
            e.dispatch(this);
        }
        return null;
    }

    @Override
    public Type analyze(WhileStmt s) {
        Type cond = s.condition.dispatch(this);
        if (!BOOL_TYPE.equals(cond)) {
            err(s, "Condition expression must be of type bool.");
        }
        for (Stmt i : s.body) {
            i.dispatch(this);
        }
        return null;
    }

    @Override
    public Type analyze(ForStmt s) {
        //get identifier for s.identifier
        Type t = s.identifier.dispatch(this);
        Type t1 = s.iterable.dispatch(this);
        if (!(t1 instanceof ListValueType)) {
            err(s, "Type %s is not a ListValueType", t1);
            return null;
        }

        for (Stmt i : s.body) {
            i.dispatch(this);
        }
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

    // [VAR-INIT]
    @Override
    public Type analyze(VarDef d) {
        Type t = ValueType.annotationToValueType(d.var.type);
        Type t1 = d.value.dispatch(this);
        if (leqA(t1, t)) {
            return null;
        }
        err(d, "Expected type `%s`; got type `%s`", t, t1);
        return null;
    }

    // [LIST-LITERAL]
    @Override
    public Type analyze(ListExpr e) {
        if (e.elements.isEmpty()) return e.setInferredType(EMPTY_TYPE);

        Type type = null;
        for (Expr expr : e.elements) {
            Type nextType = expr.dispatch(this);
            type = type == null ? nextType : leastUpperBound(type, nextType);
        }
        return e.setInferredType(new ListValueType(type));
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
                    ListValueType lvt1 = (ListValueType) t1;
                    ListValueType lvt2 = (ListValueType) t2;
                    Type t = leastUpperBound(lvt1.elementType, lvt2.elementType);
                    return e.setInferredType(new ListValueType(t));
                }
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                return e.setInferredType(INT_TYPE);
            case "-":
            case "*":
            case "//":
            case "%":
                if (!INT_TYPE.equals(t1) || !INT_TYPE.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                    return e.setInferredType(INT_TYPE);
                }
                return e.setInferredType(INT_TYPE);
            case "<":
            case "<=":
            case ">=":
            case ">":
                if (!INT_TYPE.equals(t1) || !INT_TYPE.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            case "==":
            case "!=":
                if ((!INT_TYPE.equals(t1) || !INT_TYPE.equals(t2)) && (!BOOL_TYPE.equals(t1) || !BOOL_TYPE.equals(t2)) && (!STR_TYPE.equals(t1) || !STR_TYPE.equals(t2))) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            case "and":
            case "or":
                if (!BOOL_TYPE.equals(t1) || !BOOL_TYPE.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            case "is":
                if (INT_TYPE.equals(t1) || INT_TYPE.equals(t2) || BOOL_TYPE.equals(t1) || BOOL_TYPE.equals(t2) || STR_TYPE.equals(t1) || STR_TYPE.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
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

    @Override
    public Type analyze(IfExpr e) {
        Type t1 = e.condition.dispatch(this);
        Type t2 = e.thenExpr.dispatch(this);
        Type t3 = e.elseExpr.dispatch(this);

        if (BOOL_TYPE.equals(t1)) {
            err(e, "Condition expression must be of type bool.");
        }
        return e.setInferredType(leastUpperBound(t2, t3));

    }

//Classes
    /**@Override public Type analyze(ClassType c){
    String name = c.className;

    return c.setInferredType(name_type);

    }**/

}
