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

package at.pointhi.irbuilder.irwriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.llvm.Sulong;
import com.oracle.truffle.llvm.Sulong.LLVMLanguageProvider;
import com.oracle.truffle.llvm.parser.BitcodeParserResult;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;

@TruffleLanguage.Registration(name = "SourceParser", version = "0.01", mimeType = {SourceParser.LLVM_BITCODE_MIME_TYPE, SourceParser.LLVM_BITCODE_BASE64_MIME_TYPE,
                SourceParser.SULONG_LIBRARY_MIME_TYPE})
public class SourceParser extends LLVMLanguage {

    public static final LLVMLanguageProvider provider = getProvider();

    private Env environment;

    @Override
    public LLVMContext findLLVMContext() {
        return getContextReference().get();
    }

    @Override
    protected LLVMContext createContext(Env env) {
        environment = env;
        return provider.createContext(env);
    }

    @Override
    public Env getEnvironment() {
        return environment;
    }

    @Override
    protected Object findExportedSymbol(LLVMContext context, String globalName, boolean onlyExplicit) {
        return null; // not required
    }

    @Override
    protected Object getLanguageGlobal(LLVMContext context) {
        return context;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        throw new AssertionError();
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
        Source source = request.getSource();
        return provider.parse(this, findLLVMContext(), source, request.getArgumentNames().toArray(new String[request.getArgumentNames().size()]));
    }

    private static Sulong.LLVMLanguageProvider getProvider() {
        return new Sulong.LLVMLanguageProvider() {
            @Override
            public LLVMContext createContext(Env env) {
                final LLVMContext context = new LLVMContext(env);
                context.setParseOnly(true);
                return context;
            }

            @Override
            public void disposeContext(LLVMContext context) {
            }

            @Override
            public CallTarget parse(LLVMLanguage language, LLVMContext context, Source code, String... argumentNames) throws IOException {
                try {
                    return parse(code);
                } catch (Throwable t) {
                    throw new IOException("Error while trying to parse " + code.getPath(), t);
                }
            }

            private CallTarget parse(Source code) {
                switch (code.getMimeType()) {
                    case SourceParser.LLVM_BITCODE_MIME_TYPE:
                    case SourceParser.LLVM_BITCODE_BASE64_MIME_TYPE:
                        // we are only interested in the parsed model
                        final ModelModule model = BitcodeParserResult.getFromSource(code).getModel();

                        // TODO: add config options to change the behavior of the output function
                        PrintWriter writer = null;

                        try {
                            final String sourceFileName = code.getPath();
                            final String actualTarget = sourceFileName.substring(0, sourceFileName.length() - ".bc".length()) + ".out.ll";
                            writer = new PrintWriter(actualTarget);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                            throw new RuntimeException("Could not open File Stream");
                        }

                        final IRWriterVersion llvmVersion = IRWriterVersion.fromEnviromentVariables();
                        IRWriter.writeIRToStream(model, llvmVersion, writer);

                        // because we are only parsing the file, there is nothing to execute
                        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(null));

                    default:
                        throw new IllegalArgumentException("unexpected mime type");
                }
            }

        };
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Please provide a file which you want to parse");
        }
        final File file = new File(args[0]);

        parseAndOutputFile(file);

        System.exit(0);
    }

    /**
     * Parse a file and output the parser result as LLVM IR.
     *
     * @param file File to parse
     */
    public static void parseAndOutputFile(File file) {
        try {
            final Source fileSource = Source.newBuilder(file).build();
            evaluateFromSource(fileSource);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void evaluateFromSource(Source fileSource) {
        Builder engineBuilder = PolyglotEngine.newBuilder();
        engineBuilder.config(SourceParser.LLVM_BITCODE_MIME_TYPE, Sulong.LLVM_SOURCE_FILE_KEY, fileSource);
        PolyglotEngine vm = engineBuilder.build();
        try {
            vm.eval(fileSource);
        } finally {
            vm.dispose();
        }
    }
}
