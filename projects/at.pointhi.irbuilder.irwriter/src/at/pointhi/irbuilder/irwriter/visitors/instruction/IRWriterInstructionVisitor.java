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

package at.pointhi.irbuilder.irwriter.visitors.instruction;

import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.enums.AtomicOrdering;
import com.oracle.truffle.llvm.parser.model.enums.Flag;
import com.oracle.truffle.llvm.parser.model.enums.SynchronizationScope;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Call;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

import at.pointhi.irbuilder.irwriter.IRWriter;
import at.pointhi.irbuilder.irwriter.IRWriterVersion;
import at.pointhi.irbuilder.irwriter.visitors.IRWriterBaseVisitor;

public class IRWriterInstructionVisitor extends IRWriterBaseVisitor implements InstructionVisitor {

    public IRWriterInstructionVisitor(IRWriterVersion.IRWriterVisitors visitors, IRWriter.PrintTarget target) {
        super(visitors, target);
    }

    protected static final String LLVMIR_LABEL_ALIGN = "align";

    protected static final String INDENTATION = "  ";

    protected void writeIndent() {
        write(INDENTATION);
    }

    private static final String LLVMIR_LABEL_ALLOCATE = "alloca";

    @Override
    public void visit(AllocateInstruction allocate) {
        writeIndent();

        // <result> = alloca <type>
        writef("%s = %s ", allocate.getName(), LLVMIR_LABEL_ALLOCATE);
        writeType(allocate.getPointeeType());

        // [, <ty> <NumElements>]
        if (!(allocate.getCount() instanceof IntegerConstant && ((IntegerConstant) allocate.getCount()).getValue() == 1)) {
            write(", ");
            writeType(allocate.getCount().getType());
            write(" ");
            writeInnerSymbolValue(allocate.getCount());
        }

        // [, align <alignment>]
        if (allocate.getAlign() != 0) {
            writef(", %s %d", LLVMIR_LABEL_ALIGN, 1 << (allocate.getAlign() - 1));
        }

        writeln();
    }

    @Override
    public void visit(BinaryOperationInstruction operation) {
        writeIndent();

        // <result> = <op>
        // sulong specific toString
        writef("%s = %s ", operation.getName(), operation.getOperator());

        // { <flag>}*
        for (Flag flag : operation.getFlags()) {
            write(flag.toString()); // sulong specific toString
            write(" ");
        }

        // <ty> <op1>, <op2>
        writeType(operation.getType());
        write(" ");
        writeInnerSymbolValue(operation.getLHS());
        write(", ");
        writeInnerSymbolValue(operation.getRHS());

        writeln();
    }

    private static final String LLVMIR_LABEL_BRANCH = "br";

    private static final String LLVMIR_LABEL_BRANCH_LABEL = "label";

    @Override
    public void visit(BranchInstruction branch) {
        writeIndent();

        writef("%s %s ", LLVMIR_LABEL_BRANCH, LLVMIR_LABEL_BRANCH_LABEL);
        writeBlockName(branch.getSuccessor());

        writeln();
    }

    static final String LLVMIR_LABEL_CALL = "call";

    @Override
    public void visit(CallInstruction call) {
        writeIndent();

        // <result> = [tail] call
        writef("%s = ", call.getName());

        writeFunctionCall(call);

        writeln();
    }

    @Override
    public void visit(CastInstruction cast) {
        writeIndent();

        // sulong specific toString
        writef("%s = %s ", cast.getName(), cast.getOperator());
        writeType(cast.getValue().getType());
        write(" ");
        writeInnerSymbolValue(cast.getValue());
        write(" to ");
        writeType(cast.getType());

        writeln();
    }

    private static final String LLVMIR_LABEL_COMPARE = "icmp";
    private static final String LLVMIR_LABEL_COMPARE_FP = "fcmp";

    @Override
    public void visit(CompareInstruction operation) {
        writeIndent();

        // <result> = <icmp|fcmp> <cond> <ty> <op1>, <op2>
        write(operation.getName());
        write(" = ");

        if (operation.getOperator().isFloatingPoint()) {
            write(LLVMIR_LABEL_COMPARE_FP);
        } else {
            write(LLVMIR_LABEL_COMPARE);
        }

        write(" ");
        write(operation.getOperator().toString()); // sulong specific toString
        write(" ");
        writeType(operation.getLHS().getType());
        write(" ");
        writeInnerSymbolValue(operation.getLHS());
        write(", ");
        writeInnerSymbolValue(operation.getRHS());

        writeln();
    }

    private static final String LLVMIR_LABEL_CONDITIONAL_BRANCH = "br";

    @Override
    public void visit(ConditionalBranchInstruction branch) {
        writeIndent();

        // br i1 <cond>, label <iftrue>, label <iffalse>
        write(LLVMIR_LABEL_CONDITIONAL_BRANCH);
        write(" ");
        writeType(branch.getCondition().getType());
        write(" ");
        writeInnerSymbolValue(branch.getCondition());
        write(", ");
        write(LLVMIR_LABEL_BRANCH_LABEL);
        write(" ");
        writeBlockName(branch.getTrueSuccessor());
        write(", ");
        write(LLVMIR_LABEL_BRANCH_LABEL);
        write(" ");
        writeBlockName(branch.getFalseSuccessor());

        writeln();
    }

    private static final String LLVMIR_LABEL_EXTRACT_ELEMENT = "extractelement";

    @Override
    public void visit(ExtractElementInstruction extract) {
        writeIndent();

        // <result> = extractelement <n x <ty>> <val>, i32 <idx>
        write(extract.getName());
        write(" = ");
        write(LLVMIR_LABEL_EXTRACT_ELEMENT);
        write(" ");
        writeType(extract.getVector().getType());
        write(" ");
        writeInnerSymbolValue(extract.getVector());
        write(", ");
        writeType(extract.getIndex().getType());
        write(" ");
        writeInnerSymbolValue(extract.getIndex());

        writeln();
    }

    private static final String LLVMIR_LABEL_EXTRACT_VALUE = "extractvalue";

    @Override
    public void visit(ExtractValueInstruction extract) {
        writeIndent();

        // <result> = extractvalue <aggregate type> <val>, <idx>{, <idx>}*
        write(extract.getName());
        write(" = ");
        write(LLVMIR_LABEL_EXTRACT_VALUE);
        write(" ");
        writeType(extract.getAggregate().getType());
        write(" ");
        writeInnerSymbolValue(extract.getAggregate());
        writef(", %d", extract.getIndex());

        writeln();
    }

    protected static final String LLVMIR_LABEL_GET_ELEMENT_POINTER = "getelementptr";

    protected static final String LLVMIR_LABEL_GET_ELEMENT_POINTER_INBOUNDS = "inbounds";

    @Override
    public void visit(GetElementPointerInstruction gep) {
        writeIndent();

        // <result> = getelementptr
        writef("%s = %s ", gep.getName(), LLVMIR_LABEL_GET_ELEMENT_POINTER);

        // [inbounds]
        if (gep.isInbounds()) {
            write(LLVMIR_LABEL_GET_ELEMENT_POINTER_INBOUNDS);
            write(" ");
        }

        // <pty>* <ptrval>
        writeType(gep.getBasePointer().getType());
        write(" ");
        writeInnerSymbolValue(gep.getBasePointer());

        // {, <ty> <idx>}*
        for (final Symbol sym : gep.getIndices()) {
            write(", ");
            writeType(sym.getType());
            write(" ");
            writeInnerSymbolValue(sym);
        }

        writeln();
    }

    private static final String LLVMIR_LABEL_INDIRECT_BRANCH = "indirectbr";

    @Override
    public void visit(IndirectBranchInstruction branch) {
        writeIndent();

        // indirectbr <somety>* <address>, [ label <dest1>, label <dest2>, ... ]
        write(LLVMIR_LABEL_INDIRECT_BRANCH);
        write(" ");
        writeType(branch.getAddress().getType());
        write(" ");
        writeInnerSymbolValue(branch.getAddress());
        write(", [ ");
        for (int i = 0; i < branch.getSuccessorCount(); i++) {
            if (i != 0) {
                write(", ");
            }
            write(LLVMIR_LABEL_BRANCH_LABEL);
            write(" ");
            writeBlockName(branch.getSuccessor(i));
        }
        write(" ]");

        writeln();
    }

    private static final String LLVMIR_LABEL_INSERT_ELEMENT = "insertelement";

    @Override
    public void visit(InsertElementInstruction insert) {
        writeIndent();

        // <result> = insertelement <n x <ty>> <val>, <ty> <elt>, i32 <idx>
        write(insert.getName());
        write(" = ");
        write(LLVMIR_LABEL_INSERT_ELEMENT);
        write(" ");
        writeType(insert.getVector().getType());
        write(" ");
        writeInnerSymbolValue(insert.getVector());
        write(", ");
        writeType(insert.getValue().getType());
        write(" ");
        writeInnerSymbolValue(insert.getValue());
        write(", ");
        writeType(insert.getIndex().getType());
        write(" ");
        writeInnerSymbolValue(insert.getIndex());

        writeln();
    }

    private static final String LLVMIR_LABEL_INSERT_VALUE = "insertvalue";

    @Override
    public void visit(InsertValueInstruction insert) {
        writeIndent();

        // <result> = insertvalue <aggregate type> <val>, <ty> <elt>, <idx>{, <idx>}*
        write(insert.getName());
        write(" = ");
        write(LLVMIR_LABEL_INSERT_VALUE);
        write(" ");
        writeType(insert.getAggregate().getType());
        write(" ");
        writeInnerSymbolValue(insert.getAggregate());
        write(", ");
        writeType(insert.getValue().getType());
        write(" ");
        writeInnerSymbolValue(insert.getValue());
        writef(", %d", insert.getIndex());

        writeln();
    }

    static final String LLVMIR_LABEL_LOAD = "load";

    static final String LLVMIR_LABEL_ATOMIC = "atomic";

    static final String LLVMIR_LABEL_VOLATILE = "volatile";

    static final String LLVMIR_LABEL_SINGLETHREAD = "singlethread";

    @Override
    public void visit(LoadInstruction load) {
        writeIndent();

        write(load.getName());
        write(" = ");
        write(LLVMIR_LABEL_LOAD);

        if (load.getAtomicOrdering() != AtomicOrdering.NOT_ATOMIC) {
            write(" ");
            write(LLVMIR_LABEL_ATOMIC);
        }

        if (load.isVolatile()) {
            write(" ");
            write(LLVMIR_LABEL_VOLATILE);
        }

        write(" ");
        writeType(load.getSource().getType());

        write(" ");
        writeInnerSymbolValue(load.getSource());

        if (load.getAtomicOrdering() != AtomicOrdering.NOT_ATOMIC) {
            if (load.getSynchronizationScope() == SynchronizationScope.SINGLE_THREAD) {
                write(" ");
                write(LLVMIR_LABEL_SINGLETHREAD);
            }

            write(" ");
            write(load.getAtomicOrdering().toString());
        }

        if (load.getAlign() != 0) {
            writef(", %s %d", LLVMIR_LABEL_ALIGN, 1 << (load.getAlign() - 1));
        }

        writeln();
    }

    private static final String LLVMIR_LABEL_PHI = "phi";

    @Override
    public void visit(PhiInstruction phi) {
        writeIndent();

        // <result> = phi <ty>
        write(phi.getName());
        write(" = ");
        write(LLVMIR_LABEL_PHI);
        write(" ");

        writeType(phi.getType());
        write(" ");

        // [ <val0>, <label0>], ...
        for (int i = 0; i < phi.getSize(); i++) {
            if (i != 0) {
                write(", ");
            }

            write("[ ");
            writeInnerSymbolValue(phi.getValue(i));
            write(", ");
            writeBlockName(phi.getBlock(i));
            write(" ]");
        }

        writeln();
    }

    private static final String LLVMIR_LABEL_RETURN = "ret";

    @Override
    public void visit(ReturnInstruction ret) {
        writeIndent();

        write(LLVMIR_LABEL_RETURN);
        write(" ");

        final Symbol value = ret.getValue();
        if (value == null) {
            writeType(MetaType.VOID);
        } else {
            writeType(value.getType());
            write(" ");
            writeInnerSymbolValue(value);
        }

        writeln();
    }

    private static final String LLVMIR_LABEL_SELECT = "select";

    @Override
    public void visit(SelectInstruction select) {
        writeIndent();

        // <result> = select selty <cond>, <ty> <val1>, <ty> <val2>
        write(select.getName());
        write(" = ");
        write(LLVMIR_LABEL_SELECT);
        write(" ");

        writeType(select.getCondition().getType());
        write(" ");
        writeInnerSymbolValue(select.getCondition());
        write(", ");

        writeType(select.getTrueValue().getType());
        write(" ");
        writeInnerSymbolValue(select.getTrueValue());
        write(", ");

        writeType(select.getFalseValue().getType());
        write(" ");
        writeInnerSymbolValue(select.getFalseValue());

        writeln();
    }

    private static final String LLVMIR_LABEL_SHUFFLE_VECTOR = "shufflevector";

    @Override
    public void visit(ShuffleVectorInstruction shuffle) {
        writeIndent();

        // <result> = shufflevector <n x <ty>> <v1>, <n x <ty>> <v2>, <m x i32> <mask>
        write(shuffle.getName());
        write(" = ");
        write(LLVMIR_LABEL_SHUFFLE_VECTOR);
        write(" ");

        writeType(shuffle.getVector1().getType());
        write(" ");
        writeInnerSymbolValue(shuffle.getVector1());
        write(", ");

        writeType(shuffle.getVector2().getType());
        write(" ");
        writeInnerSymbolValue(shuffle.getVector2());
        write(", ");

        writeType(shuffle.getMask().getType());
        write(" ");
        writeInnerSymbolValue(shuffle.getMask());

        writeln();
    }

    private static final String LLVMIR_LABEL_STORE = "store";

    @Override
    public void visit(StoreInstruction store) {
        writeIndent();

        writef("%s ", LLVMIR_LABEL_STORE);

        if (store.getAtomicOrdering() != AtomicOrdering.NOT_ATOMIC) {
            write(LLVMIR_LABEL_ATOMIC);
            write(" ");
        }

        if (store.isVolatile()) {
            write(LLVMIR_LABEL_VOLATILE);
            write(" ");
        }

        writeType(((PointerType) store.getDestination().getType()).getPointeeType());
        write(" ");

        writeInnerSymbolValue(store.getSource());
        write(", ");

        writeType(store.getDestination().getType());
        write(" ");
        writeInnerSymbolValue(store.getDestination());

        if (store.getAtomicOrdering() != AtomicOrdering.NOT_ATOMIC) {
            if (store.getSynchronizationScope() == SynchronizationScope.SINGLE_THREAD) {
                write(" ");
                write(LLVMIR_LABEL_SINGLETHREAD);
            }

            write(" ");
            write(store.getAtomicOrdering().toString()); // sulong specific toString
        }

        if (store.getAlign() != 0) {
            writef(", %s %d", LLVMIR_LABEL_ALIGN, 1 << (store.getAlign() - 1));
        }

        writeln();
    }

    private static final String LLVMIR_LABEL_SWITCH = "switch";

    @Override
    public void visit(SwitchInstruction select) {
        writeIndent();

        // switch <intty> <value>, label <defaultdest>
        write(LLVMIR_LABEL_SWITCH);
        write(" ");
        writeType(select.getCondition().getType());
        write(" ");
        writeInnerSymbolValue(select.getCondition());
        write(", ");
        write(LLVMIR_LABEL_BRANCH_LABEL);
        write(" ");
        writeBlockName(select.getDefaultBlock());

        write(" [ ");
        for (int i = 0; i < select.getCaseCount(); i++) {
            if (i != 0) {
                writeln();
                writeIndent();
                writeIndent();
            }

            final Symbol val = select.getCaseValue(i);
            final InstructionBlock blk = select.getCaseBlock(i);
            writeType(val.getType());
            write(" ");
            writeInnerSymbolValue(val);
            write(", ");
            write(LLVMIR_LABEL_BRANCH_LABEL);
            write(" ");
            writeBlockName(blk);
        }
        write(" ]");

        writeln();
    }

    private static final String LLVMIR_LABEL_SWITCH_OLD = "switch";

    @Override
    public void visit(SwitchOldInstruction select) {
        writeIndent();

        // switch <intty> <value>, label <defaultdest>
        write(LLVMIR_LABEL_SWITCH_OLD);
        write(" ");
        writeType(select.getCondition().getType());
        write(" ");
        writeInnerSymbolValue(select.getCondition());
        write(", ");
        write(LLVMIR_LABEL_BRANCH_LABEL);
        write(" ");
        writeBlockName(select.getDefaultBlock());

        write(" [ ");
        for (int i = 0; i < select.getCaseCount(); i++) {
            if (i != 0) {
                writeln();
                writeIndent();
                writeIndent();
            }

            writeType(select.getCondition().getType());
            write(String.format(" %d, ", select.getCaseValue(i)));
            write(LLVMIR_LABEL_BRANCH_LABEL);
            write(" ");
            writeBlockName(select.getCaseBlock(i));
        }
        write(" ]");

        writeln();
    }

    private static final String LLVMIR_LABEL_UNREACHABLE = "unreachable";

    @Override
    public void visit(UnreachableInstruction unreachable) {
        writeIndent();

        write(LLVMIR_LABEL_UNREACHABLE);

        writeln();
    }

    @Override
    public void visit(VoidCallInstruction call) {
        writeIndent();

        writeFunctionCall(call);

        writeln();
    }

    protected void writeFunctionCall(Call call) {
        write(LLVMIR_LABEL_CALL);
        write(" ");

        final Symbol callTarget = call.getCallTarget();
        if (callTarget instanceof FunctionType) {
            // <ty>
            final FunctionType decl = (FunctionType) callTarget;

            decl.getReturnType().accept(visitors.getTypeVisitor());

            if (decl.isVarArg() || (decl.getReturnType() instanceof PointerType && ((PointerType) decl.getReturnType()).getPointeeType() instanceof FunctionType)) {
                write(" ");
                writeFormalArguments(decl);
                write("*");
            }
            write(String.format(" %s", decl.getName()));

        } else if (callTarget instanceof CallInstruction) {
            // final FunctionType decl = ((CallInstruction) callTarget).getCallType();
            final FunctionType decl = (FunctionType) ((CallInstruction) callTarget).getCallTarget();
            decl.getReturnType().accept(visitors.getTypeVisitor());
            write(String.format(" %s", ((CallInstruction) callTarget).getName()));

        } else if (callTarget instanceof FunctionParameter) {
            callTarget.getType().accept(visitors.getTypeVisitor());
            write(String.format(" %s ", ((FunctionParameter) callTarget).getName()));

        } else if (callTarget instanceof ValueSymbol) {
            Type targetType;
            if (callTarget instanceof LoadInstruction) {
                targetType = ((LoadInstruction) callTarget).getSource().getType();
            } else {
                targetType = callTarget.getType();
            }

            while (targetType instanceof PointerType) {
                targetType = ((PointerType) targetType).getPointeeType();
            }

            if (targetType instanceof FunctionType) {
                final FunctionType decl = (FunctionType) targetType;

                decl.getReturnType().accept(visitors.getTypeVisitor());

                if (decl.isVarArg() || (decl.getReturnType() instanceof PointerType && ((PointerType) decl.getReturnType()).getPointeeType() instanceof FunctionType)) {
                    write(" ");
                    writeFormalArguments(decl);
                    write("*");
                }

                write(" ");
                writeInnerSymbolValue(callTarget);

            } else {
                throw new AssertionError("unexpected target type: " + targetType.getClass().getName());
            }

        } else if (callTarget instanceof Constant) {
            callTarget.getType().accept(visitors.getTypeVisitor());
            write(" ");
            ((Constant) callTarget).accept(visitors.getConstantVisitor());

        } else {
            throw new AssertionError("unexpected target type: " + call.getCallTarget().getClass().getName());
        }

        writeActualArgs(call);
    }

    protected void writeActualArgs(Call call) {
        write("(");
        for (int i = 0; i < call.getArgumentCount(); i++) {
            final Symbol arg = call.getArgument(i);

            if (i != 0) {
                write(", ");
            }

            writeType(arg.getType());
            write(" ");
            writeInnerSymbolValue(arg);
        }
        write(")");
    }
}