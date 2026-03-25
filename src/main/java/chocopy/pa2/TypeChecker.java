package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.ListValueType;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;

import static chocopy.common.analysis.types.Type.*;

/** Analyzer that performs ChocoPy type checks on all nodes.  Applied after
 *  collecting declarations. */
public class TypeChecker extends AbstractNodeAnalyzer<Type> {

    /** The current symbol table (changes depending on the function
     *  being analyzed). */
    private SymbolTable<Type> sym;
    /** Collector for errors. */
    private Errors errors;


    /** Creates a type checker using GLOBALSYMBOLS for the initial global
     *  symbol table and ERRORS0 to receive semantic errors. */
    public TypeChecker(SymbolTable<Type> globalSymbols, Errors errors0) {
        sym = globalSymbols;
        errors = errors0;
    }

    /** Inserts an error message in NODE if there isn't one already.
     *  The message is constructed with MESSAGE and ARGS as for
     *  String.format. */
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

    @Override
    public Type analyze(ExprStmt s) {
        s.expr.dispatch(this);
        return null;
    }

    // Literals
    @Override
    public Type analyze(IntegerLiteral i) {
        return i.setInferredType(Type.INT_TYPE);
    }
    @Override
    public Type analyze (BooleanLiteral b) { return b.setInferredType(Type.BOOL_TYPE); }
    @Override
    public Type analyze (StringLiteral s) { return s.setInferredType(Type.STR_TYPE); }
    @Override
    public Type analyze (NoneLiteral n) { return n.setInferredType(Type.NONE_TYPE); }

    // Identifiers
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
            // TODO: LCA for List Concatentation
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
            if ((!INT_TYPE.equals(t1) || !INT_TYPE.equals(t2)) && (!BOOL_TYPE.equals(t1) || !BOOL_TYPE.equals(t2)) && (!STR_TYPE.equals(t1) || !STR_TYPE.equals(t2))) {
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
            if (INT_TYPE.equals(t1) || INT_TYPE.equals(t2) || BOOL_TYPE.equals(t1) || BOOL_TYPE.equals(t2) || STR_TYPE.equals(t1) || STR_TYPE.equals(t2)) {
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
        switch(e.operator) {
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
}
