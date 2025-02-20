/*
 * Copyright (c) 2016 CUBRID Corporation.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */

package com.cubrid.plcsql.compiler.visitor;

import com.cubrid.jsp.Server;
import com.cubrid.jsp.data.ColumnInfo;
import com.cubrid.plcsql.compiler.Coercion;
import com.cubrid.plcsql.compiler.CoercionScheme;
import com.cubrid.plcsql.compiler.DBTypeAdapter;
import com.cubrid.plcsql.compiler.InstanceStore;
import com.cubrid.plcsql.compiler.Misc;
import com.cubrid.plcsql.compiler.ParseTreeConverter;
import com.cubrid.plcsql.compiler.StaticSql;
import com.cubrid.plcsql.compiler.SymbolStack;
import com.cubrid.plcsql.compiler.ast.*;
import com.cubrid.plcsql.compiler.error.SemanticError;
import com.cubrid.plcsql.compiler.serverapi.ServerAPI;
import com.cubrid.plcsql.compiler.serverapi.SqlSemantics;
import com.cubrid.plcsql.compiler.type.Type;
import com.cubrid.plcsql.compiler.type.TypeChar;
import com.cubrid.plcsql.compiler.type.TypeRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class TypeChecker extends AstVisitor<Type> {

    public TypeChecker(InstanceStore iStore, SymbolStack symbolStack, ParseTreeConverter ptConv) {
        this.iStore = iStore;
        this.symbolStack = symbolStack;
        this.ptConv = ptConv;
    }

    @Override
    public Type visitUnit(Unit node) {
        visit(node.routine);
        return null;
    }

    @Override
    public Type visitBody(Body node) {
        visitNodeList(node.stmts);
        visitNodeList(node.exHandlers);
        return null;
    }

    @Override
    public Type visitExHandler(ExHandler node) {
        visitNodeList(node.stmts);
        return null;
    }

    @Override
    public Type visitTypeSpec(TypeSpec node) {
        return node.type;
    }

    @Override
    public Type visitTypeSpecPercent(TypeSpecPercent node) {
        assert node.type != null;
        return node.type;
    }

    @Override
    public Type visitCaseExpr(CaseExpr node) {
        Type caseValType = visit(node.val);
        assert caseComparedTypes != null;
        caseComparedTypes.add(caseValType);
        return visit(node.expr);
    }

    @Override
    public Type visitCaseStmt(CaseStmt node) {
        Type caseValType = visit(node.val);
        assert caseComparedTypes != null;
        caseComparedTypes.add(caseValType);
        visitNodeList(node.stmts);
        return null;
    }

    @Override
    public Type visitCondExpr(CondExpr node) {
        Type caseCondType = visit(node.cond);
        if (caseCondType != Type.BOOLEAN) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.cond.ctx), // s202
                    "the condition must be boolean");
        }
        return visit(node.expr);
    }

    @Override
    public Type visitCondStmt(CondStmt node) {
        Type caseCondType = visit(node.cond);
        if (caseCondType != Type.BOOLEAN) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.cond.ctx), // s203
                    "type of the condition must be boolean");
        }
        visitNodeList(node.stmts);
        return null;
    }

    @Override
    public Type visitDeclParamIn(DeclParamIn node) {
        Type paramTy = visitDeclParam(node);
        if (node.hasDefault()) {
            Type defaultValTy = visit(node.defaultVal);
            Coercion c = Coercion.getCoercion(iStore, defaultValTy, paramTy);
            if (c == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(node.defaultVal.ctx), // s239
                        "type of the default value is not compatible with the parameter type");
            } else {
                node.defaultVal.setCoercion(c);
            }
        }

        return null;
    }

    @Override
    public Type visitDeclParamOut(DeclParamOut node) {
        visitDeclParam(node);
        return null;
    }

    @Override
    public Type visitDeclVar(DeclVar node) {
        visit(node.typeSpec);
        if (node.val == null) {
            assert !node.notNull; // syntactically guaranteed
        } else {
            Type valType = visit(node.val);
            if (node.notNull && valType == Type.NULL) {
                throw new SemanticError(
                        Misc.getLineColumnOf(node.val.ctx), // s204
                        "NOT NULL variables may not have null as their initial value");
            }

            Coercion c = Coercion.getCoercion(iStore, valType, node.typeSpec.type);
            if (c == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(node.val.ctx), // s205
                        "type of the initial value is not compatible with the variable's declared type");
            } else {
                node.val.setCoercion(c);
            }
        }

        return null;
    }

    @Override
    public Type visitDeclConst(DeclConst node) {
        visit(node.typeSpec);
        assert node.val != null; // syntactically guaranteed
        Type valType = visit(node.val);
        if (node.notNull && valType == Type.NULL) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.val.ctx), // s206
                    "NOT NULL constants may not have null as their initial value");
        }

        Coercion c = Coercion.getCoercion(iStore, valType, node.typeSpec.type);
        if (c == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.val.ctx), // s207
                    "type of the initial value is not compatible with the constant's declared type");
        } else {
            node.val.setCoercion(c);
        }

        return null;
    }

    @Override
    public Type visitDeclFunc(DeclFunc node) {
        return visitDeclRoutine(node);
    }

    @Override
    public Type visitDeclProc(DeclProc node) {
        return visitDeclRoutine(node);
    }

    @Override
    public Type visitDeclCursor(DeclCursor node) {

        assert node.staticSql.intoTargetList == null; // by earlier check

        visitNodeList(node.paramList);
        typeCheckHostExprs(node.staticSql); // s400
        return null;
    }

    @Override
    public Type visitDeclLabel(DeclLabel node) {
        return null; // nothing to do
    }

    @Override
    public Type visitDeclException(DeclException node) {
        return null; // nothing to do
    }

    @Override
    public Type visitExprBetween(ExprBetween node) {
        Type targetType = visit(node.target);
        Type lowerType = visit(node.lowerBound);
        Type upperType = visit(node.upperBound);

        List<Coercion> outCoercions = new ArrayList<>();
        DeclFunc op =
                symbolStack.getOperator(
                        iStore, outCoercions, "opBetween", targetType, lowerType, upperType);
        if (op == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.ctx), // s208, s209
                    "lower bound or upper bound does not have a comparable type");
        }
        assert outCoercions.size() == 3;

        if (op.hasTimestampParam()) {
            node.setOpExtension("Timestamp");
        } else if (targetType instanceof TypeChar
                && lowerType instanceof TypeChar
                && upperType instanceof TypeChar) {
            node.setOpExtension("Char");
        }

        node.target.setCoercion(outCoercions.get(0));
        node.lowerBound.setCoercion(outCoercions.get(1));
        node.upperBound.setCoercion(outCoercions.get(2));

        return Type.BOOLEAN;
    }

    private static final Set<String> comparisonOp = new HashSet<>();

    static {
        // see ParseTreeConverter.visitRel_exp()
        comparisonOp.add("Eq");
        comparisonOp.add("NullSafeEq");
        comparisonOp.add("Neq");
        comparisonOp.add("Le");
        comparisonOp.add("Ge");
        comparisonOp.add("Lt");
        comparisonOp.add("Gt");
    }

    @Override
    public Type visitExprBinaryOp(ExprBinaryOp node) {
        Type leftType = visit(node.left);
        Type rightType = visit(node.right);

        // check if it is = or != for two records of the same type
        if (leftType.idx == Type.IDX_RECORD || rightType.idx == Type.IDX_RECORD) {
            if (!"Eq".equals(node.opStr) && !"Neq".equals(node.opStr)) {
                throw new SemanticError(
                        Misc.getLineColumnOf(node.ctx), // s237
                        "only = and != are allowed for a record");
            }
            if (leftType != rightType) {
                throw new SemanticError(
                        Misc.getLineColumnOf(node.ctx), // s238
                        "= and != are allowed only for records of the same type");
            }
            node.recordTypeOfOperands = (TypeRecord) leftType;
            node.recordTypeOfOperands.generateEq = true;
            return Type.BOOLEAN;
        }

        List<Coercion> outCoercions = new ArrayList<>();
        DeclFunc binOp =
                symbolStack.getOperator(
                        iStore, outCoercions, "op" + node.opStr, leftType, rightType);
        if (binOp == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.ctx), // s210
                    "operands do not have compatible types");
        }
        assert outCoercions.size() == 2;

        if (binOp.hasTimestampParam()) {
            node.setOpExtension("Timestamp");
        } else if (comparisonOp.contains(node.opStr)
                && leftType instanceof TypeChar
                && rightType instanceof TypeChar) {
            node.setOpExtension("Char");
        }

        node.left.setCoercion(outCoercions.get(0));
        node.right.setCoercion(outCoercions.get(1));

        return binOp.retTypeSpec.type;
    }

    @Override
    public Type visitExprCase(ExprCase node) {

        List<Type> saveCaseComparedTypes = caseComparedTypes;
        caseComparedTypes = new ArrayList<>();
        List<Type> caseExprTypes = new ArrayList<>();

        // visit
        Type selectorType = visit(node.selector);
        caseComparedTypes.add(selectorType);

        Type commonType = null;
        for (CaseExpr ce : node.whenParts.nodes) {
            Type ty = visit(ce);
            commonType = getCommonType(commonType, ty);
            if (commonType == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(ce.expr.ctx), // s227
                        "expression in this case has an incompatible type " + ty);
            }
            caseExprTypes.add(ty);
        }
        if (node.elsePart != null) {
            Type ty = visit(node.elsePart);
            commonType = getCommonType(commonType, ty);
            if (commonType == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(node.elsePart.ctx), // s228
                        "expression in the else part has an incompatible type " + ty);
            }
            caseExprTypes.add(ty);
        }

        boolean comparedAreChars = true;
        for (Type ts : caseComparedTypes) {
            comparedAreChars = comparedAreChars && (ts instanceof TypeChar);
        }
        if (comparedAreChars) {
            for (CaseExpr ce : node.whenParts.nodes) {
                ce.setOpExtension("Char");
            }
        }

        List<Coercion> outCoercions = new ArrayList<>();
        DeclFunc op =
                symbolStack.getOperator(
                        iStore, outCoercions, "opIn", caseComparedTypes.toArray(TYPE_ARRAY_DUMMY));
        if (op == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.ctx), // s226
                    "one of the values does not have a comparable type");
        }
        assert outCoercions.size() == caseComparedTypes.size();

        // set coercions of selector, case values, and case expressions
        node.selector.setCoercion(outCoercions.get(0));
        int i = 1;
        for (CaseExpr ce : node.whenParts.nodes) {
            ce.val.setCoercion(outCoercions.get(i));

            Coercion c = Coercion.getCoercion(iStore, caseExprTypes.get(i - 1), commonType);
            assert c != null
                    : ("no coercion from " + caseExprTypes.get(i - 1) + " to " + commonType);
            ce.expr.setCoercion(c);

            i++;
        }
        if (node.elsePart != null) {
            Coercion c = Coercion.getCoercion(iStore, caseExprTypes.get(i - 1), commonType);
            assert c != null
                    : ("no coercion from " + caseExprTypes.get(i - 1) + " to " + commonType);
            node.elsePart.setCoercion(c);
            i++;
        }
        assert i == caseExprTypes.size() + 1;

        node.setSelectorType(op.paramList.nodes.get(0).typeSpec.type);
        node.setResultType(commonType);

        caseComparedTypes = saveCaseComparedTypes; // restore

        return commonType;
    }

    @Override
    public Type visitExprCond(ExprCond node) {
        List<Type> condExprTypes = new ArrayList<>();

        Type commonType = null;
        for (CondExpr ce : node.condParts.nodes) {
            Type ty = visitCondExpr(ce);
            commonType = getCommonType(commonType, ty);
            if (commonType == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(ce.expr.ctx), // s229
                        "expression in this case has an incompatible type " + ty);
            }
            condExprTypes.add(ty);
        }
        if (node.elsePart != null) {
            Type ty = visit(node.elsePart);
            commonType = getCommonType(commonType, ty);
            if (commonType == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(node.elsePart.ctx), // s230
                        "expression in the else part has an incompatible type " + ty);
            }
            condExprTypes.add(ty);
        }

        int i = 0;
        for (CondExpr ce : node.condParts.nodes) {

            Coercion c = Coercion.getCoercion(iStore, condExprTypes.get(i), commonType);
            assert c != null : ("no coercion from " + condExprTypes.get(i) + " to " + commonType);
            ce.expr.setCoercion(c);

            i++;
        }
        if (node.elsePart != null) {
            Coercion c = Coercion.getCoercion(iStore, condExprTypes.get(i), commonType);
            assert c != null : ("no coercion from " + condExprTypes.get(i) + " to " + commonType);
            node.elsePart.setCoercion(c);
            i++;
        }
        assert i == condExprTypes.size();

        node.setResultType(commonType);

        return commonType;
    }

    @Override
    public Type visitExprCursorAttr(ExprCursorAttr node) {
        Type idType = visitExprId(node.id);
        assert (idType == Type.CURSOR || idType == Type.SYS_REFCURSOR); // by earlier check

        return node.attr.ty;
    }

    @Override
    public Type visitExprDate(ExprDate node) {
        return Type.DATE;
    }

    @Override
    public Type visitExprDatetime(ExprDatetime node) {
        return Type.DATETIME;
    }

    @Override
    public Type visitExprFalse(ExprFalse node) {
        return Type.BOOLEAN;
    }

    @Override
    public Type visitExprField(ExprField node) {
        Type ret = null;

        DeclId declId = node.record.decl;
        Type recTy = declId.type();
        assert recTy.idx == Type.IDX_RECORD;
        if (recTy == Type.RECORD_ANY) {
            // This record is for a dynamic SQL
            // It cannot be a specific Java type: type unknown at compile time
            ret = Type.OBJECT;
        } else {
            // This record is for a static SQL
            assert recTy instanceof TypeRecord;
            TypeRecord recTy2 = (TypeRecord) recTy;

            int i = 1, found = -1;
            for (Misc.Pair<String, Type> p : recTy2.selectList) {
                if (node.fieldName.equals(p.e1)) {
                    if (found > 0) {
                        throw new SemanticError(
                                Misc.getLineColumnOf(node.ctx), // s420
                                String.format("field name '%s' is ambiguous", node.fieldName));
                    }
                    ret = p.e2;
                    found = i;
                }
                i++;
            }
            if (ret == null) {

                throw new SemanticError(
                        Misc.getLineColumnOf(node.ctx), // s401
                        String.format("no field named '%s' in the record type", node.fieldName));
            } else {

                assert found > 0;
                node.setType(ret);
                node.setColIndex(found);
            }
        }

        return ret;
    }

    @Override
    public Type visitExprGlobalFuncCall(ExprGlobalFuncCall node) {
        assert node.decl != null;
        checkRoutineCall(node.decl, node.args.nodes);
        return node.decl.retTypeSpec.type;
    }

    @Override
    public Type visitExprId(ExprId node) {
        if (node.decl instanceof DeclIdTypeSpeced) {
            return ((DeclIdTypeSpeced) node.decl).typeSpec().type;
        } else if (node.decl instanceof DeclCursor) {
            return Type.CURSOR;
        } else if (node.decl instanceof DeclForIter) {
            return Type.INT;
        } else {
            assert false : "unreachable";
            throw new RuntimeException("unreachable");
        }
    }

    @Override
    public Type visitExprIn(ExprIn node) {
        List<Expr> args = new ArrayList<>();
        List<Type> argTypes = new ArrayList<>();

        Type targetType = visit(node.target);
        args.add(node.target);
        argTypes.add(targetType);
        boolean argsAreChars = (targetType instanceof TypeChar);

        for (Expr e : node.inElements.nodes) {
            Type eType = visit(e);
            args.add(e);
            argTypes.add(eType);
            argsAreChars = argsAreChars && (eType instanceof TypeChar);
        }
        int len = args.size();

        List<Coercion> outCoercions = new ArrayList<>();
        DeclFunc op =
                symbolStack.getOperator(
                        iStore, outCoercions, "opIn", argTypes.toArray(TYPE_ARRAY_DUMMY));
        if (op == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.ctx), // s212
                    "one of the values does not have a comparable type");
        }
        assert outCoercions.size() == len;

        if (op.hasTimestampParam()) {
            node.setOpExtension("Timestamp");
        } else if (argsAreChars) {
            node.setOpExtension("Char");
        }

        for (int i = 0; i < len; i++) {
            Expr arg = args.get(i);
            Coercion c = outCoercions.get(i);
            arg.setCoercion(c);
        }

        return Type.BOOLEAN;
    }

    @Override
    public Type visitExprLike(ExprLike node) {
        Type targetType = visit(node.target);
        Coercion c = Coercion.getCoercion(iStore, targetType, Type.STRING_ANY);
        if (c == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.target.ctx), // s213
                    "tested expression cannot be coerced to a string type");
        } else {
            node.target.setCoercion(c);
        }

        Type patternType = visit(node.pattern);
        c = Coercion.getCoercion(iStore, patternType, Type.STRING_ANY);
        if (c == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.pattern.ctx), // s232
                    "pattern cannot be coerced to a string type");
        } else {
            node.pattern.setCoercion(c);
        }

        return Type.BOOLEAN;
    }

    private Type typeBuiltinFuncCall(BuiltinFuncCall node, String name, String sql) {

        List<SqlSemantics> sqlSemantics = ServerAPI.getSqlSemantics(Arrays.asList(sql));
        assert sqlSemantics.size() == 1;
        SqlSemantics ss = sqlSemantics.get(0);
        assert ss.seqNo == 0;

        if (ss.errCode == 0) {
            assert ss.selectList.size() == 1;
            ColumnInfo ci = ss.selectList.get(0);

            Type ret;
            if (DBTypeAdapter.isSupported(ci.type)) {
                ret = DBTypeAdapter.getValueType(iStore, ci.type);
            } else {
                throw new SemanticError(
                        Misc.getLineColumnOf(node.ctx), // s233
                        String.format(
                                "unsupported return type (code %d) of the built-in function %s",
                                ci.type, name));
            }

            node.setResultType(ret);
            return ret;
        } else {
            Server.log(
                    String.format(
                            "semantic check API returned an error (%d, %s) for '%s'",
                            ss.errCode, ss.errMsg, sql));
            throw new SemanticError(
                    Misc.getLineColumnOf(node.ctx), // s235
                    "function "
                            + name
                            + " is undefined or given wrong number or types of arguments");
        }
    }

    @Override
    public Type visitExprBuiltinFuncCall(ExprBuiltinFuncCall node) {

        String tvStr = checkArgsAndConvertToTypicalValuesStr(node.args.nodes, node.name);
        String sql = String.format("select %s%s from dual", node.name, tvStr);
        return typeBuiltinFuncCall(node, node.name, sql);
    }

    // -------------------------------------------------------------------------
    //

    @Override
    public Type visitExprSyntaxedCallAdddate(ExprSyntaxedCallAdddate node) {
        String tvStrOfDate = checkArgAndGetTypicalValueStr(node.date);
        String tvStrOfDelta = checkArgAndGetTypicalValueStr(node.delta);
        String sql =
                String.format(
                        "select ADDDATE(%s, INTERVAL %s %s) from dual",
                        tvStrOfDate, tvStrOfDelta, node.timeUnit);
        return typeBuiltinFuncCall(node, "ADDDATE", sql);
    }

    // -------------------------------------------------------------------------
    //

    @Override
    public Type visitExprSyntaxedCallCast(ExprSyntaxedCallCast node) {
        if (node.arg instanceof ExprNull) {
            // Special case: the argument is NULL
            //  . in this case, we do not need ask the server of the type
            //  . moreover, the server returns an unsupported type code 5 (DB_OBJECT) in this case.
            node.setResultType(node.tySpec.type);
            return node.resultType;
        } else {
            String tvStrOfArg = checkArgAndGetTypicalValueStr(node.arg);
            String sql =
                    String.format(
                            "select CAST(%s AS %s) from dual",
                            tvStrOfArg, node.tySpec.type.plcName);
            return typeBuiltinFuncCall(node, "CAST", sql);
        }
    }

    // -------------------------------------------------------------------------
    //

    @Override
    public Type visitExprSyntaxedCallChr(ExprSyntaxedCallChr node) {
        String tvStrOfArg = checkArgAndGetTypicalValueStr(node.arg);
        String sql =
                String.format(
                        "select CHR(%s USING %s) from dual",
                        tvStrOfArg, node.isUtf8 ? "utf8" : "iso88591");
        return typeBuiltinFuncCall(node, "CHR", sql);
    }

    // -------------------------------------------------------------------------
    //

    @Override
    public Type visitExprSyntaxedCallExtract(ExprSyntaxedCallExtract node) {
        String tvStrOfArg = checkArgAndGetTypicalValueStr(node.arg);
        String sql =
                String.format("select EXTRACT(%s FROM %s) from dual", node.timeField, tvStrOfArg);
        return typeBuiltinFuncCall(node, "EXTRACT", sql);
    }

    // -------------------------------------------------------------------------
    //

    @Override
    public Type visitExprSyntaxedCallPosition(ExprSyntaxedCallPosition node) {
        String tvStrOfSub = checkArgAndGetTypicalValueStr(node.sub);
        String tvStrOfWhole = checkArgAndGetTypicalValueStr(node.whole);
        String sql = String.format("select POSITION(%s IN %s) from dual", tvStrOfSub, tvStrOfWhole);
        return typeBuiltinFuncCall(node, "POSITION", sql);
    }

    // -------------------------------------------------------------------------
    //

    @Override
    public Type visitExprSyntaxedCallSubdate(ExprSyntaxedCallSubdate node) {
        String tvStrOfDate = checkArgAndGetTypicalValueStr(node.date);
        String tvStrOfDelta = checkArgAndGetTypicalValueStr(node.delta);
        String sql =
                String.format(
                        "select SUBDATE(%s, INTERVAL %s %s) from dual",
                        tvStrOfDate, tvStrOfDelta, node.timeUnit);
        return typeBuiltinFuncCall(node, "SUBDATE", sql);
    }

    // -------------------------------------------------------------------------
    //

    @Override
    public Type visitExprSyntaxedCallTrim(ExprSyntaxedCallTrim node) {
        String tvStrOfTrimStr =
                node.trimStr == null ? "" : checkArgAndGetTypicalValueStr(node.trimStr);
        String tvStrOfStr = checkArgAndGetTypicalValueStr(node.str);
        String sql =
                String.format(
                        "select TRIM(%s %s FROM %s) from dual",
                        node.trimDir, tvStrOfTrimStr, tvStrOfStr);
        return typeBuiltinFuncCall(node, "TRIM", sql);
    }

    // -------------------------------------------------------------------------
    //

    @Override
    public Type visitExprLocalFuncCall(ExprLocalFuncCall node) {
        checkRoutineCall(node.decl, node.args.nodes);
        return node.decl.retTypeSpec.type;
    }

    @Override
    public Type visitExprNull(ExprNull node) {
        return Type.NULL;
    }

    @Override
    public Type visitExprUint(ExprUint node) {
        return node.ty;
    }

    @Override
    public Type visitExprFloat(ExprFloat node) {
        return node.ty;
    }

    @Override
    public Type visitExprSerialVal(ExprSerialVal node) {
        assert node.verified;
        return Type.NUMERIC_ANY;
    }

    @Override
    public Type visitExprSqlRowCount(ExprSqlRowCount node) {
        return Type.BIGINT;
    }

    @Override
    public Type visitExprStr(ExprStr node) {
        return TypeChar.getInstance(iStore, TypeChar.MAX_LEN);
    }

    @Override
    public Type visitExprTime(ExprTime node) {
        return Type.TIME;
    }

    @Override
    public Type visitExprTrue(ExprTrue node) {
        return Type.BOOLEAN;
    }

    @Override
    public Type visitExprUnaryOp(ExprUnaryOp node) {
        Type operandType = visit(node.operand);

        List<Coercion> outCoercions = new ArrayList<>();
        DeclFunc unaryOp =
                symbolStack.getOperator(iStore, outCoercions, "op" + node.opStr, operandType);
        if (unaryOp == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.ctx), // s215
                    "argument does not have a compatible type");
        }
        assert !unaryOp.hasTimestampParam();

        node.operand.setCoercion(outCoercions.get(0));

        return unaryOp.retTypeSpec.type;
    }

    @Override
    public Type visitExprTimestamp(ExprTimestamp node) {
        return Type.TIMESTAMP;
    }

    @Override
    public Type visitExprAutoParam(ExprAutoParam node) {
        return node.getType(iStore); // NOTE: unused yet
    }

    @Override
    public Type visitExprSqlCode(ExprSqlCode node) {
        return Type.INT;
    }

    @Override
    public Type visitExprSqlErrm(ExprSqlErrm node) {
        return Type.STRING_ANY;
    }

    @Override
    public Type visitStmtAssign(StmtAssign node) {
        Type valType = visit(node.val);
        Type targetType = visit(node.target);

        if (node.target instanceof ExprId) {
            ExprId targetId = (ExprId) node.target;
            boolean checkNotNull =
                    (targetId.decl instanceof DeclVar) && ((DeclVar) targetId.decl).notNull;
            if (checkNotNull && valType == Type.NULL) {
                throw new SemanticError(
                        Misc.getLineColumnOf(node.val.ctx), // s231
                        "NOT NULL constraint violation");
            }
        }

        Coercion c = Coercion.getCoercion(iStore, valType, targetType);
        if (c == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.val.ctx), // s216
                    "type of assigned value is not compatible with the type of target variable");
        } else {
            node.val.setCoercion(c);
        }

        return null;
    }

    @Override
    public Type visitStmtBasicLoop(StmtBasicLoop node) {
        visitNodeList(node.stmts);
        return null;
    }

    @Override
    public Type visitStmtBlock(StmtBlock node) {
        if (node.decls != null) {
            visitNodeList(node.decls);
        }
        visitBody(node.body);
        return null;
    }

    @Override
    public Type visitStmtExit(StmtExit node) {
        return null; // nothing to do
    }

    @Override
    public Type visitStmtCase(StmtCase node) {

        List<Type> saveCaseComparedTypes = caseComparedTypes;
        caseComparedTypes = new ArrayList<>();

        Type selectorType = visit(node.selector);
        caseComparedTypes.add(selectorType);

        visitNodeList(node.whenParts);
        if (node.elsePart != null) {
            visitNodeList(node.elsePart);
        }

        boolean comparedAreChars = true;
        for (Type ts : caseComparedTypes) {
            comparedAreChars = comparedAreChars && (ts instanceof TypeChar);
        }
        if (comparedAreChars) {
            for (CaseStmt cs : node.whenParts.nodes) {
                cs.setOpExtension("Char");
            }
        }

        List<Coercion> outCoercions = new ArrayList<>();
        DeclFunc op =
                symbolStack.getOperator(
                        iStore, outCoercions, "opIn", caseComparedTypes.toArray(TYPE_ARRAY_DUMMY));
        if (op == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.ctx), // s201
                    "one of the values does not have a comparable type");
        }
        assert outCoercions.size() == caseComparedTypes.size();

        // set coercions of selector, case values, and case expressions
        node.selector.setCoercion(outCoercions.get(0));
        int i = 1;
        for (CaseStmt cs : node.whenParts.nodes) {
            cs.val.setCoercion(outCoercions.get(i));
            i++;
        }
        assert i == caseComparedTypes.size();

        node.setSelectorType(op.paramList.nodes.get(0).typeSpec.type);

        caseComparedTypes = saveCaseComparedTypes; // restore

        return null;
    }

    @Override
    public Type visitStmtCommit(StmtCommit node) {
        return null; // nothing to do
    }

    @Override
    public Type visitStmtContinue(StmtContinue node) {
        return null; // nothing to do
    }

    @Override
    public Type visitStmtCursorClose(StmtCursorClose node) {
        Type idType = visit(node.id);
        assert (idType == Type.CURSOR || idType == Type.SYS_REFCURSOR); // by earlier check
        return null;
    }

    @Override
    public Type visitStmtCursorFetch(StmtCursorFetch node) {

        Type idType = visit(node.id);
        if (idType == Type.CURSOR) {
            assert node.columnTypeList != null;
        } else {
            assert idType == Type.SYS_REFCURSOR;
            assert node.columnTypeList == null;
        }

        List<Coercion> coercions = new ArrayList<>();

        int i = 0;
        for (Expr intoTarget : node.intoTargetList) {

            assert intoTarget instanceof AssignTarget;

            Type srcTy = (node.columnTypeList == null) ? Type.OBJECT : node.columnTypeList.get(i);
            Type dstTy = visit(intoTarget);

            Coercion c = Coercion.getCoercion(iStore, srcTy, dstTy);
            if (c == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(intoTarget.ctx), // s403
                        String.format(
                                "type of column %d of the cursor is not compatible with the type of variable %s",
                                i + 1, ((AssignTarget) intoTarget).name()));
            } else {
                coercions.add(c);
            }

            i++;
        }
        node.setCoercions(coercions);

        return null;
    }

    private void checkCursorArgs(ExprId cursor, NodeList<Expr> args) {

        DeclCursor declCursor = (DeclCursor) cursor.decl;
        int len = args.nodes.size();
        for (int i = 0; i < len; i++) {
            Expr arg = args.nodes.get(i);
            Type argType = visit(arg);
            Type paramType = declCursor.paramList.nodes.get(i).typeSpec().type;
            assert paramType != null;
            Coercion c = Coercion.getCoercion(iStore, argType, paramType);
            if (c == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(arg.ctx), // s219
                        String.format("argument %d to the cursor has an incompatible type", i + 1));
            } else {
                arg.setCoercion(c);
            }
        }
    }

    @Override
    public Type visitStmtCursorOpen(StmtCursorOpen node) {
        Type idType = visit(node.cursor);
        if (idType == Type.CURSOR) {
            checkCursorArgs(node.cursor, node.args); // s219
        } else {
            assert false : "unreachable"; // by earlier check
            throw new RuntimeException("unreachable");
        }
        return null;
    }

    @Override
    public Type visitStmtExecImme(StmtExecImme node) {

        // type of sql must be STRING
        Type sqlType = visit(node.sql);
        if (sqlType.idx != Type.IDX_STRING) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.sql.ctx), // s221
                    "SQL in the EXECUTE IMMEDIATE statement must be of a string type");
        }

        // check types of expressions in the USING clause
        if (node.usedExprList != null) {
            for (Expr e : node.usedExprList) {
                Type tyUsedExpr = visit(e);
                switch (tyUsedExpr.idx) {
                    case Type.IDX_CURSOR:
                    case Type.IDX_RECORD:
                    case Type.IDX_BOOLEAN:
                    case Type.IDX_SYS_REFCURSOR:
                        throw new SemanticError(
                                Misc.getLineColumnOf(e.ctx), // s430
                                "expressions in a USING clause cannot be of "
                                        + tyUsedExpr.plcName
                                        + " type");
                    default:; // OK
                }
            }
        }

        if (node.intoTargetList != null) {

            List<Coercion> coercions = new ArrayList<>();

            // check types of into-variables
            for (Expr intoTarget : node.intoTargetList) {

                assert intoTarget instanceof AssignTarget;

                Type tyIntoTarget = visit(intoTarget);
                Coercion c = Coercion.getCoercion(iStore, Type.OBJECT, tyIntoTarget);
                if (c == null) {
                    throw new SemanticError( // s421
                            Misc.getLineColumnOf(intoTarget.ctx),
                            "into-variable "
                                    + ((AssignTarget) intoTarget).name()
                                    + " has an incompatible type "
                                    + tyIntoTarget.plcName);
                } else {
                    coercions.add(c);
                }
            }
            node.setCoercions(coercions);
        }

        return null;
    }

    @Override
    public Type visitStmtStaticSql(StmtStaticSql node) {

        StaticSql staticSql = node.staticSql;

        typeCheckHostExprs(staticSql); // s404

        if (node.intoTargetList != null) {

            List<Coercion> coercions = new ArrayList<>();

            // check types of into-targets
            int i = 0;
            for (Misc.Pair<String, Type> p : staticSql.selectList) {
                Type tyColumn = p.e2;
                Expr intoTarget = node.intoTargetList.get(i);
                assert intoTarget instanceof AssignTarget;
                Type tyIntoTarget = visit(intoTarget);
                Coercion c = Coercion.getCoercion(iStore, tyColumn, tyIntoTarget);
                if (c == null) {
                    throw new SemanticError( // s405
                            Misc.getLineColumnOf(staticSql.ctx),
                            ((AssignTarget) intoTarget).name()
                                    + " in the INTO clause has an incompatible type");
                } else {
                    coercions.add(c);
                }

                i++;
            }
            node.setCoercions(coercions);
        }

        return null;
    }

    @Override
    public Type visitStmtForCursorLoop(StmtForCursorLoop node) {

        Type idType = visit(node.cursor);
        if (idType == Type.CURSOR) {
            checkCursorArgs(node.cursor, node.cursorArgs); // s240
        } else {
            assert false : "unreachable"; // by earlier check
            throw new RuntimeException("unreachable");
        }

        visitNodeList(node.stmts);

        return null;
    }

    @Override
    public Type visitStmtForIterLoop(StmtForIterLoop node) {
        Type ty;
        Coercion c;

        ty = visit(node.lowerBound);
        c = Coercion.getCoercion(iStore, ty, Type.INT);
        if (c == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.lowerBound.ctx), // s222
                    "lower bounds of FOR loops must have a type compatible with INT");
        } else {
            node.lowerBound.setCoercion(c);
        }

        ty = visit(node.upperBound);
        c = Coercion.getCoercion(iStore, ty, Type.INT);
        if (c == null) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.upperBound.ctx), // s223
                    "upper bounds of FOR loops must have a type compatible with INT");
        } else {
            node.upperBound.setCoercion(c);
        }

        if (node.step != null) {
            ty = visit(node.step);
            c = Coercion.getCoercion(iStore, ty, Type.INT);
            if (c == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(node.step.ctx), // s224
                        "steps of FOR loops must have a type compatible with INT");
            } else {
                node.step.setCoercion(c);
            }
        }

        visitNodeList(node.stmts);

        return null;
    }

    @Override
    public Type visitStmtForStaticSqlLoop(StmtForStaticSqlLoop node) {

        typeCheckHostExprs(node.staticSql); // s406
        visitNodeList(node.stmts);
        return null;
    }

    @Override
    public Type visitStmtGlobalProcCall(StmtGlobalProcCall node) {
        assert node.decl != null;
        checkRoutineCall(node.decl, node.args.nodes);
        return null;
    }

    @Override
    public Type visitStmtIf(StmtIf node) {
        visitNodeList(node.condStmtParts);
        if (node.elsePart != null) {
            visitNodeList(node.elsePart);
        }
        return null;
    }

    @Override
    public Type visitStmtLocalProcCall(StmtLocalProcCall node) {
        checkRoutineCall(node.decl, node.args.nodes);
        return null;
    }

    @Override
    public Type visitStmtNull(StmtNull node) {
        return null; // nothing to do
    }

    @Override
    public Type visitStmtOpenFor(StmtOpenFor node) {
        Type ty = visitExprId(node.id);
        assert ty == Type.SYS_REFCURSOR; // by earlier check

        assert node.staticSql != null;
        assert node.staticSql.intoTargetList == null; // by earlier check

        typeCheckHostExprs(node.staticSql); // s407
        return null;
    }

    @Override
    public Type visitStmtRaise(StmtRaise node) {
        return null; // nothing to do
    }

    @Override
    public Type visitStmtRaiseAppErr(StmtRaiseAppErr node) {
        Type ty;

        ty = visit(node.errCode);
        if (ty != Type.INT) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.errCode.ctx), // s220
                    "error codes must be an INT");
        }

        ty = visit(node.errMsg);
        if (ty.idx != Type.IDX_STRING) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.errMsg.ctx), // s218
                    "error messages must be a string");
        }

        return null;
    }

    @Override
    public Type visitStmtReturn(StmtReturn node) {
        if (node.retVal != null) {
            Type valType = visit(node.retVal);
            Coercion c = Coercion.getCoercion(iStore, valType, node.retTypeSpec.type);
            if (c == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(node.retVal.ctx), // s217
                        "type of the return value is not compatible with the return type");
            } else {
                node.retVal.setCoercion(c);
            }
        }
        return null;
    }

    @Override
    public Type visitStmtRollback(StmtRollback node) {
        return null; // nothing to do
    }

    @Override
    public Type visitStmtWhileLoop(StmtWhileLoop node) {
        Type condType = visit(node.cond);
        if (condType != Type.BOOLEAN) {
            throw new SemanticError(
                    Misc.getLineColumnOf(node.cond.ctx), // s211
                    "while loops' condition must be of BOOLEAN type");
        }
        visitNodeList(node.stmts);
        return null;
    }

    @Override
    public Type visitExName(ExName node) {
        assert false : "unreachable";
        throw new RuntimeException("unreachable");
    }

    // ------------------------------------------------------------------
    // Private
    // ------------------------------------------------------------------

    private static final Type[] TYPE_ARRAY_DUMMY = new Type[0];

    private InstanceStore iStore;
    private SymbolStack symbolStack;
    private ParseTreeConverter ptConv;

    private List<Type> caseComparedTypes;

    private Type getCommonType(Type former, Type delta) {
        if (former == null) {
            return delta;
        } else {
            return CoercionScheme.getCommonType(former, delta);
        }
    }

    private Type visitDeclParam(DeclParam node) {
        return visit(node.typeSpec);
    }

    private Type visitDeclRoutine(DeclRoutine node) {
        visitNodeList(node.paramList);
        if (node.retTypeSpec != null) {
            visit(node.retTypeSpec);
        }
        if (node.decls != null) {
            visitNodeList(node.decls);
        }
        assert node.body != null; // syntactically guaranteed
        visitBody(node.body);
        return null;
    }

    private String checkArgAndGetTypicalValueStr(Expr arg) {

        String ret;
        if (arg instanceof SqlLiteral) {
            if (arg.ctx == null) {
                // unreachable
                throw new RuntimeException(
                        "unreachable: a built-in function argument without a context");
            } else {
                ret = arg.ctx.getText();
            }
        } else {
            Type argType = visit(arg);
            ret = argType.typicalValueStr;
        }

        return ret;
    }

    private String checkArgsAndConvertToTypicalValuesStr(List<Expr> args, String funcName) {

        if (args.size() == 0) {
            if (SymbolStack.noParenBuiltInFunc.indexOf(funcName) >= 0) {
                return "";
            } else {
                return "()";
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("(");

        int len = args.size();
        for (int i = 0; i < len; i++) {

            Expr arg = args.get(i);
            String typicalValueStr = checkArgAndGetTypicalValueStr(arg);

            if (typicalValueStr == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(arg.ctx), // s234
                        String.format(
                                "argument %d to the built-in function %s has an invalid type",
                                i + 1, funcName));
            }

            if (i > 0) {
                sb.append(", ");
            }
            sb.append(typicalValueStr);
        }

        sb.append(")");
        return sb.toString();
    }

    private void checkRoutineCall(DeclRoutine decl, List<Expr> args) {
        int len = args.size();
        for (int i = 0; i < len; i++) {
            Expr arg = args.get(i);
            Type argType = visit(arg);
            DeclParam declParam = decl.paramList.nodes.get(i);
            Type paramType = declParam.typeSpec().type;
            assert paramType
                    != null; // TODO: paramType can be null if variadic parameters are introduced
            Coercion c = Coercion.getCoercion(iStore, argType, paramType);
            if (c == null) {
                throw new SemanticError(
                        Misc.getLineColumnOf(arg.ctx), // s214
                        String.format(
                                "argument %d to the call of %s has an incompatible type %s",
                                i + 1, Misc.detachPkgName(decl.name), argType.plcName));
            } else {
                if (declParam instanceof DeclParamOut && c.getReversion(iStore) == null) {
                    throw new SemanticError(
                            Misc.getLineColumnOf(arg.ctx), // s236
                            String.format(
                                    "OUT/INOUT parameter %d has a type %s which is incompatible with the argument type %s",
                                    i + 1, paramType.plcName, argType.plcName));
                }

                arg.setCoercion(c);
            }
        }
    }

    private void typeCheckHostExprs(StaticSql staticSql) {

        assert staticSql.ctx != null;

        LinkedHashMap<Expr, Type> hostExprs = staticSql.hostExprs;
        for (Expr e : hostExprs.keySet()) {
            Type ty = visit(e);
            if (ty == Type.BOOLEAN || ty == Type.CURSOR || ty == Type.SYS_REFCURSOR) {
                throw new SemanticError(
                        Misc.getLineColumnOf(e.ctx),
                        "host expressions cannot be of either BOOLEAN, CURSOR or SYS_REFCURSOR type");
            }

            /* TODO
            Type tyRequired = hostExprs.get(e);
            if (tyRequired != null) {
                ...
            }
             */
        }
    }
}
