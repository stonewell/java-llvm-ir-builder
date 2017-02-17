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

package at.pointhi.irbuilder.irwriter.visitors;

import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

import at.pointhi.irbuilder.irwriter.IRWriter;
import at.pointhi.irbuilder.irwriter.IRWriterVersion;

public class IRWriterBaseVisitor {

    protected final IRWriterVersion.IRWriterVisitors visitors;

    private final IRWriter.PrintTarget out;

    public IRWriterBaseVisitor(IRWriterVersion.IRWriterVisitors visitors, IRWriter.PrintTarget target) {
        this.visitors = visitors;
        this.out = target;
    }

    protected void write(String s) {
        out.print(s);
    }

    protected void writeln(String s) {
        out.println(s);
    }

    protected void writeln() {
        out.println();
    }

    @SuppressWarnings("unused")
    protected void writeType(Type type) {
        throw new RuntimeException("Not implemented yet!");
    }

    @SuppressWarnings("unused")
    protected void writeConstant(Constant constant) {
        throw new RuntimeException("Not implemented yet!");
    }

    protected void writeFunction(FunctionDefinition function) {
        function.accept(visitors.getFunctionVisitor());
    }

    protected void writeInstructionBlock(InstructionBlock block) {
        block.accept(visitors.getInstructionVisitor());
    }

    protected void writeInnerSymbolValue(Symbol symbol) {
        if (symbol instanceof ValueSymbol) {
            final String value = ((ValueSymbol) symbol).getName();
            write(value);
        } else if (symbol instanceof Constant) {
            writeConstant((Constant) symbol);
        } else {
            throw new IllegalStateException("Cannot print this value: " + symbol);
        }
    }

    protected void writeBlockName(InstructionBlock block) {
        final String name = block.getName();
        if (LLVMIdentifier.isImplicitBlockName(name)) {
            write("%");
            write(LLVMIdentifier.extractLabelFromImplicitBlockName(name));
        } else {
            write(name);
        }
    }

}
