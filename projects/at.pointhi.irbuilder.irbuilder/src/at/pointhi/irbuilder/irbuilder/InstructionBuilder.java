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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.llvm.parser.model.attributes.AttributesCodeEntry;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.enums.BinaryOperator;
import com.oracle.truffle.llvm.parser.model.enums.CastOperator;
import com.oracle.truffle.llvm.parser.model.enums.CompareOperator;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.Symbols;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.InlineAsmConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.DataSpecConverter;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.OpaqueType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

import at.pointhi.irbuilder.irbuilder.util.ConstantUtil;

// TODO: https://github.com/pointhi/sulong/blob/1cc13ee850034242fd3406e29cd003b06f065c15/projects/com.oracle.truffle.llvm.writer/src/com/oracle/truffle/llvm/writer/facades/InstructionGeneratorFacade.java
public class InstructionBuilder {
    private static final String x86TargetDataLayout = "e-p:64:64:64-i1:8:8-i8:8:8-i16:16:16-i32:32:32-i64:64:64-f32:32:32-f64:64:64-v64:64:64-v128:128:128-a0:0:64-s0:64:64-f80:128:128-n8:16:32:64-S128";
    public static final DataSpecConverter targetDataLayout = DataLayoutConverter.getConverter(x86TargetDataLayout);

    private final FunctionDefinition function;

    private InstructionBlock curBlock;

    private int counter = 1;
    private int argCounter = 1;

    public InstructionBuilder(FunctionDefinition function) {
        this.function = function;
        this.curBlock = function.generateBlock();
    }

    public FunctionParameter createParameter(Type type) {
        function.createParameter(type);
        FunctionParameter newParam = function.getParameters().get(function.getParameters().size() - 1);
        newParam.setName("arg_" + Integer.toString(argCounter++));
        return newParam;
    }

    /**
     * Go to next InstructionBlock and give them a unique label. If required it generates a new one
     * one the fly.
     *
     * @return reference to new InstructionBlock
     */
    public InstructionBlock nextBlock() {
        final int nextBlockIdx = curBlock.getBlockIndex() + 1;

        ensureBlockExists(nextBlockIdx);

        curBlock = function.generateBlock();
        curBlock.setName("label_" + Integer.toString(nextBlockIdx));
        return curBlock;
    }

    /**
     * Get the current InstructionBlock.
     *
     * @return reference to current InstructionBlock
     */
    public InstructionBlock getCurrentBlock() {
        return curBlock;
    }

    /**
     * Get the following InstructionBlock. If there does not exists one, it creates a new one.
     *
     * @return reference to next InstructionBlock
     */
    public InstructionBlock getNextBlock() {
        return getBlock(curBlock.getBlockIndex() + 1);
    }

    /**
     * Get a InstructionBlock defined by a specific index. If there does not exists one, it creates
     * enough new blocks to cover the requested index and all new indexes between.
     *
     * It should be noted that this instruction is only returning the same instruction block for
     * sure, when the requested block index is less or equal the current one. It's possible to
     * inject InstructionBlocks, which can result in changed indexes after those blocks.
     *
     * Thus it is recommended to get all block indexes at the beginning of a function, before
     * calling advanced generator code.
     *
     * @return reference to requested InstructionBlock
     */
    public InstructionBlock getBlock(int idx) {
        ensureBlockExists(idx);

        return function.getBlock(idx);
    }

    private void ensureBlockExists(int idx) {
        if (idx < function.getBlocks().size()) {
            return; // block already exists, nothing to do
        }

        /*
         * it seems we need to manually allocate new blocks
         *
         * Because the required field is private, we rely on reflection for now.
         */
        try {
            // get private blocks field and make it public
            final Field dataField = function.getClass().getDeclaredField("blocks");
            dataField.setAccessible(true);

            // get InstructionBlock[] and reallocate to new size
            final InstructionBlock[] oldBlocks = (InstructionBlock[]) dataField.get(function);
            final InstructionBlock[] newBlocks = Arrays.copyOf(oldBlocks, idx + 1);

            // we need to initialize our new InstructionBlock elements
            for (int i = oldBlocks.length; i < newBlocks.length; i++) {
                newBlocks[i] = new InstructionBlock(i);
            }

            // write new InstructionBlock[] back into the object
            dataField.set(function, newBlocks);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Append a specific amount of blocks after the current one, and change all InstructionBlock
     * indexes accordingly.
     *
     * @param count number of Blocks which we want to insert
     */
    public void insertBlocks(int count) {
        try {
            // get private blocks field and make it public
            final Field dataField = function.getClass().getDeclaredField("blocks");
            dataField.setAccessible(true);

            // get InstructionBlock[] and reallocate to new size
            final InstructionBlock[] oldBlocks = (InstructionBlock[]) dataField.get(function);
            final InstructionBlock[] newBlocks = Arrays.copyOf(oldBlocks, oldBlocks.length + count);

            final int insertIdx = curBlock.getBlockIndex() + 1;
            final int rearIdx = insertIdx + count;

            // copy the rear part of the array to the new position
            System.arraycopy(newBlocks, insertIdx, newBlocks, rearIdx, oldBlocks.length - insertIdx);

            // we need to initialize our new InstructionBlock elements
            for (int i = insertIdx; i < rearIdx; i++) {
                newBlocks[i] = new InstructionBlock(i);
            }

            // get private blockIndex field and make it public
            final Field blockIndexField = InstructionBlock.class.getDeclaredField("blockIndex");
            blockIndexField.setAccessible(true);

            // we need to update the index of the remaining instructions
            for (int i = rearIdx; i < newBlocks.length; i++) {
                blockIndexField.set(newBlocks[i], i);
            }

            // write new InstructionBlock[] back into the object
            dataField.set(function, newBlocks);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This function has to be called at the end of the definition, to adjust some internals.
     */
    public void exitFunction() {
        function.exitFunction();
    }

    public FunctionDefinition getFunctionDefinition() {
        return function;
    }

    public int getArgCounter() {
        return argCounter;
    }

    private Symbols getSymbols() {
        return function.getSymbols();
    }

    /**
     * Add a new Symbol to the Symbol list, and return it's given symbol position.
     */
    private int addSymbol(Symbol sym) {
        Symbols symbols = getSymbols();
        symbols.addSymbol(sym);
        return symbols.getSize() - 1; // return index of new symbol
    }

    /**
     * Get the last instruction added to the function.
     */
    protected Instruction getLastInstruction() {
        Instruction lastInstr = curBlock.getInstruction(curBlock.getInstructionCount() - 1);
        return lastInstr;
    }

    /**
     * Append Instruction to current Block, and update name if required.
     */
    protected Instruction appendAndReturnInstruction(Instruction instr) {
        curBlock.append(instr);
        if (instr instanceof ValueInstruction) {
            ValueInstruction lastValueInstr = (ValueInstruction) instr;
            if (lastValueInstr.getName().equals(LLVMIdentifier.UNKNOWN)) {
                lastValueInstr.setName(Integer.toString(counter++));
            }
        }
        return instr;
    }

    private static int calculateAlign(int align) {
        assert Integer.highestOneBit(align) == align;

        return align == 0 ? 0 : Integer.numberOfTrailingZeros(align) + 1;
    }

    public Instruction createAllocate(Type type) {
        Type pointerType = new PointerType(type);
        int count = addSymbol(ConstantUtil.getI32Const(1));
        int align = type.getAlignment(targetDataLayout);

        Instruction instr = AllocateInstruction.fromSymbols(getSymbols(), pointerType, count, align);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createAtomicLoad(Type type, Instruction source, int align, boolean isVolatile, long atomicOrdering, long synchronizationScope) {
        int sourceIdx = addSymbol(source);

        Instruction instr = LoadInstruction.fromSymbols(getSymbols(), type, sourceIdx, calculateAlign(align), isVolatile, atomicOrdering, synchronizationScope);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createAtomicStore(Instruction destination, Instruction source, int align, boolean isVolatile, long atomicOrdering, long synchronizationScope) {
        int destinationIdx = addSymbol(destination);
        int sourceIdx = addSymbol(source);

        Instruction instr = StoreInstruction.fromSymbols(getSymbols(), destinationIdx, sourceIdx, calculateAlign(align), isVolatile, atomicOrdering, synchronizationScope);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createBinaryOperation(Symbol lhs, Symbol rhs, BinaryOperator op) {
        Type type = lhs.getType();
        int flagbits = 0; // TODO: flags are not supported yet
        int lhsIdx = addSymbol(lhs);
        int rhsIdx = addSymbol(rhs);

        Instruction instr = BinaryOperationInstruction.fromSymbols(getSymbols(), type, op.ordinal(), flagbits, lhsIdx, rhsIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createBranch(InstructionBlock block) {
        Instruction instr = BranchInstruction.fromTarget(block);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createBranch(Symbol condition, InstructionBlock ifBlock, InstructionBlock elseBlock) {
        int conditionIdx = addSymbol(condition);

        Instruction instr = ConditionalBranchInstruction.fromSymbols(getSymbols(), conditionIdx, ifBlock, elseBlock);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createCall(Symbol target, Symbol[] arguments) {
        final FunctionType functionType;
        if (target.getType() instanceof FunctionType) {
            functionType = ((FunctionType) target.getType());
        } else {
            Type pointeeType;
            if (target instanceof LoadInstruction) {
                pointeeType = ((LoadInstruction) target).getSource().getType();
            } else if (target instanceof InlineAsmConstant) {
                pointeeType = ((InlineAsmConstant) target).getType();
            } else {
                throw new RuntimeException("cannot handle target type: " + target.getClass().getName());
            }

            while (pointeeType instanceof PointerType) {
                pointeeType = ((PointerType) pointeeType).getPointeeType();
            }
            if (pointeeType instanceof FunctionType) {
                functionType = ((FunctionType) pointeeType);
            } else {
                throw new RuntimeException("cannot handle target type: " + pointeeType.getClass().getName());
            }
        }

        Type returnType = functionType.getReturnType();

        int targetIdx = addSymbol(target);
        int[] argumentsIdx = new int[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            argumentsIdx[i] = addSymbol(arguments[i]);
        }

        Instruction instr;
        if (VoidType.INSTANCE.equals(returnType)) {
            instr = VoidCallInstruction.fromSymbols(getSymbols(), targetIdx, argumentsIdx, AttributesCodeEntry.EMPTY);
        } else {
            instr = CallInstruction.fromSymbols(getSymbols(), returnType, targetIdx, argumentsIdx, AttributesCodeEntry.EMPTY);
        }
        return appendAndReturnInstruction(instr);
    }

    public Instruction createCast(Type type, CastOperator op, Symbol value) {
        int valueIdx = addSymbol(value);

        Instruction instr = CastInstruction.fromSymbols(getSymbols(), type, op.ordinal(), valueIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createCompare(CompareOperator op, Symbol lhs, Symbol rhs) {
        Type type = lhs.getType();
        int lhsIdx = addSymbol(lhs);
        int rhsIdx = addSymbol(rhs);

        Instruction instr = CompareInstruction.fromSymbols(getSymbols(), type, op.getIrIndex(), lhsIdx, rhsIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createExtractValue(@SuppressWarnings("unused") Instruction struct, Symbol vector, int index) {
        Type type = ((AggregateType) vector.getType()).getElementType(index); // TODO: correct?
        int vectorIdx = addSymbol(vector);
        int indexIdx = addSymbol(ConstantUtil.getI32Const(index));

        Instruction instr = ExtractElementInstruction.fromSymbols(getSymbols(), type, vectorIdx, indexIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createExtractElement(Instruction vector, int index) {
        Type type = ((AggregateType) vector.getType()).getElementType(index);
        int vectorIdx = addSymbol(vector);
        int indexIdx = addSymbol(ConstantUtil.getI32Const(index));

        Instruction instr = ExtractElementInstruction.fromSymbols(getSymbols(), type, vectorIdx, indexIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createGetElementPointer(Symbol base, Symbol[] indices, boolean isInbounds) {
        int pointerIdx = addSymbol(base);
        List<Integer> indicesIdx = new ArrayList<>(indices.length);
        Type instrType = base.getType();
        for (int i = 0; i < indices.length; i++) {
            indicesIdx.add(addSymbol(indices[i]));

            GetElementPointerTypeVisitor localTypeVisitor = new GetElementPointerTypeVisitor(instrType, indices[i]);
            instrType.accept(localTypeVisitor);
            instrType = localTypeVisitor.getNewType();
        }

        Instruction instr = GetElementPointerInstruction.fromSymbols(getSymbols(), new PointerType(instrType), pointerIdx, indicesIdx, isInbounds);
        return appendAndReturnInstruction(instr);
    }

    private static final class GetElementPointerTypeVisitor implements TypeVisitor {

        private final Symbol idx;
        private Type newType;

        GetElementPointerTypeVisitor(Type curType, Symbol idx) {
            this.newType = curType;
            this.idx = idx;
        }

        public Type getNewType() {
            return newType;
        }

        public void visit(OpaqueType opaqueType) {
        }

        public void visit(VoidType vectorType) {
        }

        public void visit(VariableBitWidthType vectorType) {
        }

        public void visit(VectorType vectorType) {
            newType = vectorType.getElementType();
        }

        public void visit(StructureType structureType) {
            IntegerConstant idxConst = (IntegerConstant) idx;
            newType = structureType.getElementType((int) idxConst.getValue());
        }

        public void visit(ArrayType arrayType) {
            newType = arrayType.getElementType();
        }

        public void visit(PointerType pointerType) {
            newType = pointerType.getPointeeType();
        }

        public void visit(MetaType metaType) {
        }

        public void visit(PrimitiveType primitiveType) {
        }

        public void visit(FunctionType functionType) {
        }
    }

    public Instruction createIndirectBranch(Symbol address, InstructionBlock[] successors) {
        int[] successorsIdx = Arrays.stream(successors).mapToInt(f -> f.getBlockIndex()).toArray();
        int addressIdx = addSymbol(address);

        Instruction instr = IndirectBranchInstruction.generate(function, addressIdx, successorsIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createInsertElement(Instruction vector, Constant value, int index) {
        Type type = vector.getType();
        int vectorIdx = addSymbol(vector);
        int valueIdx = addSymbol(value);
        int indexIdx = addSymbol(new IntegerConstant(PrimitiveType.I32, index));

        Instruction instr = InsertElementInstruction.fromSymbols(getSymbols(), type, vectorIdx, indexIdx, valueIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createInsertValue(Instruction struct, Symbol aggregate, int index, Symbol value) {
        Type type = struct.getType(); // TODO: correct?
        int valueIdx = addSymbol(value);
        int aggregateIdx = addSymbol(aggregate);

        Instruction instr = InsertValueInstruction.fromSymbols(getSymbols(), type, aggregateIdx, index, valueIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createLoad(Instruction source) {
        Type type = ((PointerType) source.getType()).getPointeeType();
        int sourceIdx = addSymbol(source);
        int align = type.getAlignment(targetDataLayout);
        // because we don't have any optimizations, we can set isVolatile to false
        boolean isVolatile = false;

        Instruction instr = LoadInstruction.fromSymbols(getSymbols(), type, sourceIdx, calculateAlign(align), isVolatile);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createPhi(Type type, Symbol[] values, InstructionBlock[] blocks) {
        assert values.length == blocks.length;

        int[] valuesIdx = new int[values.length];

        Instruction instr = PhiInstruction.generate(function, type, valuesIdx, blocks);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createReturn() {
        Instruction instr = ReturnInstruction.generate();
        return appendAndReturnInstruction(instr);
    }

    public Instruction createReturn(Symbol value) {
        int valueIdx = addSymbol(value);

        Instruction instr = ReturnInstruction.generate(getSymbols(), valueIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createSelect(Type type, Symbol condition, Symbol trueValue, Symbol falseValue) {
        int conditionIdx = addSymbol(condition);
        int trueValueIdx = addSymbol(trueValue);
        int falseValueIdx = addSymbol(falseValue);

        Instruction instr = SelectInstruction.fromSymbols(getSymbols(), type, conditionIdx, trueValueIdx, falseValueIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createShuffleVector(Type type, Symbol vector1, Symbol vector2, Symbol mask) {
        int vector1Idx = addSymbol(vector1);
        int vector2Idx = addSymbol(vector2);
        int maskIdx = addSymbol(mask);

        Instruction instr = ShuffleVectorInstruction.fromSymbols(getSymbols(), type, vector1Idx, vector2Idx, maskIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createStore(Symbol destination, Symbol source, int align) {
        int destinationIdx = addSymbol(destination);
        int sourceIdx = addSymbol(source);
        // because we don't have any optimizations, we can set isVolatile to false
        boolean isVolatile = false;

        Instruction instr = StoreInstruction.fromSymbols(getSymbols(), destinationIdx, sourceIdx, calculateAlign(align), isVolatile);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createSwitch(Symbol condition, InstructionBlock defaultBlock, Symbol[] caseValues, InstructionBlock[] caseBlocks) {
        assert caseValues.length == caseBlocks.length;

        int conditionIdx = addSymbol(condition);
        int defaultBlockIdx = defaultBlock.getBlockIndex();

        int[] caseValuesIdx = new int[caseValues.length];
        int[] caseBlocksIdx = new int[caseBlocks.length];
        for (int i = 0; i < caseBlocks.length; i++) {
            caseValuesIdx[i] = addSymbol(caseValues[i]);
            caseBlocksIdx[i] = caseBlocks[i].getBlockIndex();
        }

        Instruction instr = SwitchInstruction.generate(function, conditionIdx, defaultBlockIdx, caseValuesIdx, caseBlocksIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createSwitchOld(Symbol condition, InstructionBlock defaultBlock, long[] caseConstants, InstructionBlock[] caseBlocks) {
        assert caseConstants.length == caseBlocks.length;

        int conditionIdx = addSymbol(condition);
        int defaultBlockIdx = defaultBlock.getBlockIndex();

        int[] caseBlocksIdx = new int[caseBlocks.length];
        for (int i = 0; i < caseBlocks.length; i++) {
            caseBlocksIdx[i] = caseBlocks[i].getBlockIndex();
        }

        Instruction instr = SwitchOldInstruction.generate(function, conditionIdx, defaultBlockIdx, caseConstants, caseBlocksIdx);
        return appendAndReturnInstruction(instr);
    }

    public Instruction createUnreachable() {
        Instruction instr = UnreachableInstruction.generate();
        return appendAndReturnInstruction(instr);
    }
}
