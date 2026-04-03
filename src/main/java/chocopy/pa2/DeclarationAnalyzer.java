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
    // Maps classNames to superClassNames
    private HashMap<String, String> classTree = new HashMap<String, String>();

    /** A new declaration analyzer sending errors to ERRORS0. */
    public DeclarationAnalyzer(Errors errors0) {
        errors = errors0;
        sym.put("object", Type.OBJECT_TYPE);
        sym.put("int", Type.INT_TYPE);
        sym.put("bool", Type.BOOL_TYPE);
        sym.put("str", Type.STR_TYPE);

        classTree.put("object", null);
        classTree.put("int", "object");
        classTree.put("bool", "object");
        classTree.put("str", "object");
    }

    public SymbolTable<Type> getGlobals() {
        return globals;
    }
    public HashMap<String, String> getClassTree() { return classTree; }

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
    public Type analyze(GlobalDecl globalDecl) {
        // Only global scopes
        return globals.get(globalDecl.variable.name);
    }

    @Override
    public Type analyze(NonLocalDecl nonLocalDecl) {
        // Parent frames that are not the global frame
        String name = nonLocalDecl.variable.name;
        SymbolTable<Type> curSym = sym.getParent();
        while (curSym != null && curSym != globals) {
            if (curSym.declares(name)) {
                return curSym.get(name);
            }
            curSym = curSym.getParent();
        }
        return null;
    }

    @Override
    public Type analyze(FuncDef funcDef) {
        List<ValueType> paramTypes = new ArrayList<>();
        for (TypedVar param : funcDef.params) {
            paramTypes.add(ValueType.annotationToValueType(param.type));
        }
        ValueType returnType = ValueType.annotationToValueType(funcDef.returnType);
        FuncType funcType = new FuncType(paramTypes, returnType);
        SymbolTable<Type> parent = sym;
        sym = new SymbolTable<>(parent);
        // Place function parameters into symbol table.
        for (TypedVar param : funcDef.params) {
            String paramName = param.identifier.name;
            ValueType paramType = ValueType.annotationToValueType(param.type);
            if (sym.declares(paramName)) {
                errors.semError(param.identifier, "Duplicate declaration of identifier in same scope: %s", paramName);
            } else {
                sym.put(paramName, paramType);
            }
        }
        // Place function declarations into symbol table.
        declareDeclarations(funcDef.declarations);
        sym = parent;
        return funcType;
    }

    @Override
    public Type analyze(ClassDef classDef) {
        String className = classDef.name.name;
        String superClassName = classDef.superClass.name;

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
        // Place class declarations into symbol table.
        declareDeclarations(classDef.declarations);
        sym = parent;
        classTree.put(className, superClassName);
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
