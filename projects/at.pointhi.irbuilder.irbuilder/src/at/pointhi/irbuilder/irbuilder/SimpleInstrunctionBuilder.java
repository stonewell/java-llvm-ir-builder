/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2017, Thomas Pointhuber
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 *  list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 *
 * Neither the name of the copyright holder nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package at.pointhi.irbuilder.irbuilder;

import java.math.BigInteger;

import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.enums.BinaryOperator;
import com.oracle.truffle.llvm.parser.model.enums.CastOperator;
import com.oracle.truffle.llvm.parser.model.enums.CompareOperator;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.FloatingPointConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

import at.pointhi.irbuilder.irbuilder.helper.LLVMIntrinsics;
import at.pointhi.irbuilder.irbuilder.helper.LLVMIntrinsics.VA_LIST_TAG_TYPE;
import at.pointhi.irbuilder.irbuilder.util.ConstantUtil;

public class SimpleInstrunctionBuilder {
    private final ModelModuleBuilder modelBuilder;
    private final InstructionBuilder builder;

    public SimpleInstrunctionBuilder(ModelModuleBuilder modelBuilder, FunctionDefinition function) {
        this(modelBuilder, new InstructionBuilder(function));
    }

    public SimpleInstrunctionBuilder(ModelModuleBuilder modelBuilder, InstructionBuilder builder) {
        this.modelBuilder = modelBuilder;
        this.builder = builder;
    }

    private static Constant toConstant(Type type, double value) {
        if (!PrimitiveType.isFloatingpointType(type)) {
            throw new AssertionError("unexpected type: " + type);
        }
        return FloatingPointConstant.create(type, new long[]{Double.doubleToRawLongBits(value)});
    }

    private static Constant toConstant(Type type, boolean value) {
        if (PrimitiveType.isIntegerType(type)) {
            return new IntegerConstant(type, value ? 1 : 0);
        } else if (PrimitiveType.isFloatingpointType(type)) {
            return toConstant(type, value ? 1. : 0.);
        } else {
            throw new AssertionError("unexpected type: " + type);
        }
    }

    private static Constant toConstant(Type type, long value) {
        if (PrimitiveType.isIntegerType(type)) {
            return new IntegerConstant(type, value);
        } else if (PrimitiveType.isFloatingpointType(type)) {
            return toConstant(type, (double) value);
        } else {
            throw new AssertionError("unexpected type: " + type);
        }
    }

    private static Constant toConstant(Type type, BigInteger value) {
        if (PrimitiveType.isIntegerType(type)) {
            if (type instanceof VariableBitWidthType) {
                return new BigIntegerConstant(type, value); // TODO
            } else {
                return new IntegerConstant(type, value.longValue());
            }
        } else if (PrimitiveType.isFloatingpointType(type)) {
            return toConstant(type, (double) value.longValue());
        } else {
            throw new AssertionError("unexpected type: " + type);
        }
    }

    public InstructionBuilder getInstructionBuilder() {
        return builder;
    }

    public FunctionParameter nextParameter() {
        final Type[] types = builder.getFunctionDefinition().getType().getArgumentTypes();
        final int id = builder.getArgCounter();
        final Type paramType;
        if (types.length <= id) {
            paramType = types[id - 1];
        } else {
            paramType = types[types.length - 1];
        }

        return builder.createParameter(paramType);
    }

    // Allocate
    public Instruction allocate(Type type) {
        return builder.createAllocate(type);
    }

    // Binary Operator
    public Instruction binaryOperator(BinaryOperator op, Symbol lhs, Symbol rhs) {
        return builder.createBinaryOperation(lhs, rhs, op);
    }

    public Instruction binaryOperator(BinaryOperator op, boolean lhs, Symbol rhs) {
        return builder.createBinaryOperation(toConstant(rhs.getType(), lhs), rhs, op);
    }

    public Instruction binaryOperator(BinaryOperator op, Symbol lhs, boolean rhs) {
        return builder.createBinaryOperation(lhs, toConstant(lhs.getType(), rhs), op);
    }

    public Instruction binaryOperator(BinaryOperator op, long lhs, Symbol rhs) {
        return builder.createBinaryOperation(toConstant(rhs.getType(), lhs), rhs, op);
    }

    public Instruction binaryOperator(BinaryOperator op, Symbol lhs, long rhs) {
        return builder.createBinaryOperation(lhs, toConstant(lhs.getType(), rhs), op);
    }

    public Instruction binaryOperator(BinaryOperator op, BigInteger lhs, Symbol rhs) {
        return builder.createBinaryOperation(toConstant(rhs.getType(), lhs), rhs, op);
    }

    public Instruction binaryOperator(BinaryOperator op, Symbol lhs, BigInteger rhs) {
        return builder.createBinaryOperation(lhs, toConstant(lhs.getType(), rhs), op);
    }

    public Instruction binaryOperator(BinaryOperator op, double lhs, Symbol rhs) {
        return builder.createBinaryOperation(toConstant(rhs.getType(), lhs), rhs, op);
    }

    public Instruction binaryOperator(BinaryOperator op, Symbol lhs, double rhs) {
        return builder.createBinaryOperation(lhs, toConstant(lhs.getType(), rhs), op);
    }

    // Compare
    public Instruction compare(CompareOperator op, Symbol lhs, Symbol rhs) {
        return builder.createCompare(op, lhs, rhs);
    }

    public Instruction compare(CompareOperator op, boolean lhs, Symbol rhs) {
        return builder.createCompare(op, toConstant(rhs.getType(), lhs), rhs);
    }

    public Instruction compare(CompareOperator op, Symbol lhs, boolean rhs) {
        return builder.createCompare(op, lhs, toConstant(lhs.getType(), rhs));
    }

    public Instruction compare(CompareOperator op, long lhs, Symbol rhs) {
        return builder.createCompare(op, toConstant(rhs.getType(), lhs), rhs);
    }

    public Instruction compare(CompareOperator op, Symbol lhs, long rhs) {
        return builder.createCompare(op, lhs, toConstant(lhs.getType(), rhs));
    }

    public Instruction compare(CompareOperator op, BigInteger lhs, Symbol rhs) {
        return builder.createCompare(op, toConstant(rhs.getType(), lhs), rhs);
    }

    public Instruction compare(CompareOperator op, Symbol lhs, BigInteger rhs) {
        return builder.createCompare(op, lhs, toConstant(lhs.getType(), rhs));
    }

    public Instruction compare(CompareOperator op, double lhs, Symbol rhs) {
        return builder.createCompare(op, toConstant(rhs.getType(), lhs), rhs);
    }

    public Instruction compare(CompareOperator op, Symbol lhs, double rhs) {
        return builder.createCompare(op, lhs, toConstant(lhs.getType(), rhs));
    }

    // Compare Vector (constructed node)
    public Instruction compareVector(CompareOperator op, Symbol lhs, Symbol rhs) {
        Instruction cmpVec = compare(op, lhs, rhs);
        AggregateType type = (AggregateType) cmpVec.getType();

        final BinaryOperator resultComperator;
        switch (op) {
            case INT_NOT_EQUAL:
            case FP_ORDERED_NOT_EQUAL:
            case FP_UNORDERED_NOT_EQUAL:
                resultComperator = BinaryOperator.INT_OR;
                break;
            default:
                resultComperator = BinaryOperator.INT_AND;
                break;
        }

        Instruction cmpRes = extractElement(cmpVec, 0);
        for (int i = 1; i < type.getNumberOfElements(); i++) {
            Instruction extr = extractElement(cmpVec, i);
            // all vector results needs to be joined together
            cmpRes = binaryOperator(resultComperator, cmpRes, extr);
        }

        return cmpRes;
    }

    // Call
    public Instruction call(Symbol target, Symbol... arguments) {
        return builder.createCall(target, arguments);
    }

    // Extract Element
    public Instruction extractElement(Instruction vector, int index) {
        return builder.createExtractElement(vector, index);
    }

    // Fill Vector (constructed node)
    public Instruction fillVector(Instruction source, Constant... values) {
        Instruction vector = load(source);
        for (int i = 0; i < values.length; i++) {
            vector = insertElement(vector, values[i], i);
        }
        return vector;
    }

    public Instruction fillVector(Instruction source, boolean... values) {
        Instruction vector = load(source);
        for (int i = 0; i < values.length; i++) {
            vector = insertElement(vector, values[i], i);
        }
        return vector;
    }

    public Instruction fillVector(Instruction source, long... values) {
        Instruction vector = load(source);
        for (int i = 0; i < values.length; i++) {
            vector = insertElement(vector, values[i], i);
        }
        return vector;
    }

    public Instruction fillVector(Instruction source, BigInteger... values) {
        Instruction vector = load(source);
        for (int i = 0; i < values.length; i++) {
            vector = insertElement(vector, values[i], i);
        }
        return vector;
    }

    public Instruction fillVector(Instruction source, double... values) {
        Instruction vector = load(source);
        for (int i = 0; i < values.length; i++) {
            vector = insertElement(vector, values[i], i);
        }
        return vector;
    }

    // Insert Element
    public Instruction insertElement(Instruction vector, Constant value, int index) {
        return builder.createInsertElement(vector, value, index);
    }

    public Instruction insertElement(Instruction vector, boolean value, int index) {
        AggregateType type = (AggregateType) vector.getType();
        return builder.createInsertElement(vector, toConstant(type.getElementType(index), value), index);
    }

    public Instruction insertElement(Instruction vector, long value, int index) {
        AggregateType type = (AggregateType) vector.getType();
        return builder.createInsertElement(vector, toConstant(type.getElementType(index), value), index);
    }

    public Instruction insertElement(Instruction vector, BigInteger value, int index) {
        AggregateType type = (AggregateType) vector.getType();
        return builder.createInsertElement(vector, toConstant(type.getElementType(index), value), index);
    }

    public Instruction insertElement(Instruction vector, double value, int index) {
        AggregateType type = (AggregateType) vector.getType();
        return builder.createInsertElement(vector, toConstant(type.getElementType(index), value), index);
    }

    // Load
    public Instruction load(Instruction source) {
        return builder.createLoad(source);
    }

    // Return
    public void returnx(Symbol value) {
        builder.createReturn(value);
    }

    public void returnx() {
        builder.createReturn();
    }

    // va_arg for x86_64-unknown-linux-gnu
    public void vaStartAMD64(Symbol vaListTag) {
        Instruction vaArrayPtr = builder.createGetElementPointer(vaListTag, new Symbol[]{ConstantUtil.getI32Const(0), ConstantUtil.getI32Const(0)}, true);
        Instruction vaBytePtr = builder.createCast(new PointerType(PrimitiveType.I8), CastOperator.BITCAST, vaArrayPtr);
        call(LLVMIntrinsics.getLlvmVaStart(modelBuilder), vaBytePtr);
    }

    public void vaEndAMD64(Symbol vaListTag) {
        Instruction vaArrayPtr = builder.createGetElementPointer(vaListTag, new Symbol[]{ConstantUtil.getI32Const(0), ConstantUtil.getI32Const(0)}, true);
        Instruction vaBytePtr = builder.createCast(new PointerType(PrimitiveType.I8), CastOperator.BITCAST, vaArrayPtr);
        call(LLVMIntrinsics.getLlvmVaEnd(modelBuilder), vaBytePtr);
    }

    /**
     * Get the next VarArg of a AMD64 compiled function declared by the %struct.__va_list_tag
     * variable.
     *
     * Please note because of the complexity of this function, that this function is adding
     * additional InstructionBlocks into the function. This needs to be considered when a branch is
     * jumping over this function.
     *
     * @see "https://software.intel.com/sites/default/files/article/402129/mpx-linux64-abi.pdf"
     *
     * @param vaListTag our %struct.__va_list_tag
     * @param type type of the variable we want to get
     * @return the next vararg
     */
    public Instruction vaArgAMD64(Symbol vaListTag, Type type) {
        final int curBlockIdx = builder.getCurrentBlock().getBlockIndex();
        builder.insertBlocks(3);
        InstructionBlock i11 = builder.getBlock(curBlockIdx + 1);
        InstructionBlock i17 = builder.getBlock(curBlockIdx + 2);
        InstructionBlock i22 = builder.getBlock(curBlockIdx + 3);

        // Is register available?
        Instruction i7 = builder.createGetElementPointer(vaListTag, new Symbol[]{ConstantUtil.getI32Const(0), ConstantUtil.getI32Const(0)}, true);
        final Instruction i8;
        final Instruction i9;
        final Instruction i10;
        if (Type.isIntegerType(type)) {
            i8 = builder.createGetElementPointer(i7, new Symbol[]{ConstantUtil.getI32Const(0), ConstantUtil.getI32Const(VA_LIST_TAG_TYPE.GP_OFFSET.getIdx())}, true);
            i9 = this.load(i8);
            i10 = compare(CompareOperator.INT_UNSIGNED_LESS_OR_EQUAL, i9, 40);
        } else if (Type.isFloatingpointType(type)) {
            i8 = builder.createGetElementPointer(i7, new Symbol[]{ConstantUtil.getI32Const(0), ConstantUtil.getI32Const(VA_LIST_TAG_TYPE.FP_OFFSET.getIdx())}, true);
            i9 = this.load(i8);
            i10 = compare(CompareOperator.INT_UNSIGNED_LESS_OR_EQUAL, i9, 160);
        } else {
            throw new AssertionError("type not implemented yet: " + type);
        }
        builder.createBranch(i10, i11, i17);

        builder.nextBlock(); // 11
        assert builder.getCurrentBlock() == i11;

        // Address of saved register
        Instruction i12 = builder.createGetElementPointer(i7, new Symbol[]{ConstantUtil.getI32Const(0), ConstantUtil.getI32Const(VA_LIST_TAG_TYPE.REG_SAVE_AREA.getIdx())}, true);
        Instruction i13 = load(i12);
        Instruction i14 = builder.createGetElementPointer(i13, new Symbol[]{i9}, false);
        Instruction i15 = builder.createCast(new PointerType(type), CastOperator.BITCAST, i14);

        // Update gp_offset
        final int offset;
        if (Type.isIntegerType(type)) {
            offset = 8;
        } else if (Type.isFloatingpointType(type)) {
            offset = 16;
        } else {
            throw new AssertionError("type not implemented yet: " + type);
        }
        Instruction i16 = binaryOperator(BinaryOperator.INT_ADD, i9, offset);
        builder.createStore(i8, i16, 16);
        builder.createBranch(i22);

        builder.nextBlock(); // 17
        assert builder.getCurrentBlock() == i17;

        // Address of stack slot
        Instruction i18 = builder.createGetElementPointer(i7, new Symbol[]{ConstantUtil.getI32Const(0), ConstantUtil.getI32Const(VA_LIST_TAG_TYPE.OVERFLOW_ARG_AREA.getIdx())}, true);
        Instruction i19 = load(i18);
        Instruction i20 = builder.createCast(new PointerType(type), CastOperator.BITCAST, i19);
        Instruction i21 = builder.createGetElementPointer(i19, new Symbol[]{ConstantUtil.getI32Const(8)}, false);
        // update to next available stack slot
        builder.createStore(i18, i21, 8);
        builder.createBranch(i22);

        builder.nextBlock(); // 22
        assert builder.getCurrentBlock() == i22;
        Instruction i23 = builder.createPhi(new PointerType(type), new Symbol[]{i15, i20}, new InstructionBlock[]{i11, i17});
        // Load argument
        Instruction i24 = load(i23);

        return i24;
    }

    // TODO: private
    public Instruction vaArgAMD64StackOnly(Symbol vaListTag, AggregateType type) {
        Instruction i7 = builder.createGetElementPointer(vaListTag, new Symbol[]{ConstantUtil.getI32Const(0), ConstantUtil.getI32Const(0)}, true);

        // Address of stack slot
        Instruction i8 = builder.createGetElementPointer(i7, new Symbol[]{ConstantUtil.getI32Const(0), ConstantUtil.getI32Const(VA_LIST_TAG_TYPE.OVERFLOW_ARG_AREA.getIdx())}, true);
        Instruction i9 = load(i8);
        Instruction i10 = builder.createCast(new PointerType(type), CastOperator.BITCAST, i9);
        // TODO: align
        Instruction i11 = builder.createGetElementPointer(i9, new Symbol[]{ConstantUtil.getI32Const(type.getSize(InstructionBuilder.targetDataLayout))}, false);

        // update to next available stack slot
        builder.createStore(i8, i11, 8);

        // copy into new object
        Instruction i4 = allocate(type);
        Instruction i12 = builder.createCast(new PointerType(PrimitiveType.I8), CastOperator.BITCAST, i4);
        Instruction i13 = builder.createCast(new PointerType(PrimitiveType.I8), CastOperator.BITCAST, i10);

        // TODO: align
        call(LLVMIntrinsics.getLlvmMemcpyP0i8P0i8i64(modelBuilder), i12, i13, ConstantUtil.getI64Const(type.getSize(InstructionBuilder.targetDataLayout)), ConstantUtil.getI32Const(4),
                        ConstantUtil.getI1Const(false));

        return i4;
    }

}
