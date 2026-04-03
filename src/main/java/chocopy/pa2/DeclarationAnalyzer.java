package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;
import sun.jvm.hotspot.debugger.cdbg.Sym;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes declarations to create a top-level symbol table.
 */
public class DeclarationAnalyzer extends AbstractNodeAnalyzer<Type> {

    /** Current symbol table. Changes with new declarative region. */
    private SymbolTable<Type> sym = new SymbolTable<>();
    /** Global symbol table. */
    private final SymbolTable<Type> globals = sym;
    /** Receiver for semantic error messages. */
    private final Errors errors;

    /** A new declaration analyzer sending errors to ERRORS0. */
    public DeclarationAnalyzer(Errors errors0) {
        errors = errors0;
        sym.put("object", Type.OBJECT_TYPE);
        sym.put("int", Type.INT_TYPE);
        sym.put("bool", Type.BOOL_TYPE);
        sym.put("str", Type.STR_TYPE);
    }

    public SymbolTable<Type> getGlobals() {
        return globals;
    }

    @Override
    public Type analyze(Program program) {
        declareDeclarations(program.declarations);
        return null;
    }

    @Override
    public Type analyze(VarDef varDef) {
        return ValueType.annotationToValueType(varDef.var.type);
    }

    @Override
    public Type analyze(FuncDef funcDef) {
        List<ValueType> paramTypes = new ArrayList<>();
        for (TypedVar param : funcDef.params) {
            paramTypes.add(ValueType.annotationToValueType(param.type));
        }
        ValueType returnType = ValueType.annotationToValueType(funcDef.returnType);
        return new FuncType(paramTypes, returnType);
    }

    @Override
    public Type analyze(ClassDef classDef) {
        String superClassName = classDef.superClass.name;
        String className = classDef.name.name;

        Type superType = sym.get(superClassName);
        if (superType == null) {
            errors.semError(classDef.superClass, "Super-class not defined: %s", superClassName);
        } else if (!(superType instanceof ClassValueType)) {
            errors.semError(classDef.superClass, "Super-class must be a class: %s", superClassName);
        } else if (Type.INT_TYPE.equals(superType) || Type.BOOL_TYPE.equals(superType) || Type.STR_TYPE.equals(superType)) {
            errors.semError(classDef.superClass, "Cannot extend special class: %s", superClassName);
        }

        SymbolTable<Type> parent= sym;
        sym = new SymbolTable<>(parent);
        declareDeclarations(classDef.declarations);
        sym = parent;
        return new ClassValueType(className);
    }

    private void declareDeclarations(List<Declaration> declarations) {
        for (Declaration decl : declarations) {
            Identifier id = decl.getIdentifier();
            String name = id.name;

            Type type = decl.dispatch(this);

            if (type == null) {
                continue;
            }

            if (sym.declares(name)) {
                errors.semError(id,
                        "Duplicate declaration of identifier in same "
                                + "scope: %s",
                        name);
            } else {
                sym.put(name, type);
            }
        }
    }

}
