/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021-2026 ishland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and to permit persons to whom the Software is furnished to
 * do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.ishland.c2me.cuda;

import java.util.regex.Pattern;

public final class CudaSourceTranspiler {
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "__attribute__\\s*\\(\\((?:aligned\\([^)]*\\)|reqd_work_group_size\\([^)]*\\)|const|pure|noinline)\\)\\)");
    private static final Pattern POINTER_ASSIGNMENT = Pattern.compile(
            "(?m)^(\\s*)(const\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\*([^=]*=\\s*)(ptr_shift_global\\([^;]+\\)|df_data_offset_global\\([^;]+\\)|rw_data|ctx\\.rw_data);$");
    private static final Pattern COMPOUND_LITERAL = Pattern.compile("\\(([A-Za-z_][A-Za-z0-9_]*_t)\\)\\s*\\{");

    private CudaSourceTranspiler() {
    }

    public static String transpile(String openClSource) {
        String source = openClSource
                .replace("typedef long int64_t;", "typedef long long int64_t;")
                .replace("typedef unsigned long uint64_t;", "typedef unsigned long long uint64_t;")
                .replaceAll("(?<![A-Za-z0-9_])([0-9]+)L\\b", "$1LL")
                .replace("0xffffffffU", "0xffffffffU")
                .replace("0xffffffffffffffffLU", "0xffffffffffffffffULL")
                .replace("0x7fffffffffffffffLU", "0x7fffffffffffffffLL")
                .replace("#pragma OPENCL FP_CONTRACT OFF", "#pragma fp_contract(off)")
                .replaceAll("\\bget_global_id\\(0\\)", "(blockIdx.x * blockDim.x + threadIdx.x)")
                .replaceAll("\\bget_global_id\\(1\\)", "(blockIdx.y * blockDim.y + threadIdx.y)")
                .replaceAll("\\bget_global_id\\(2\\)", "(blockIdx.z * blockDim.z + threadIdx.z)")
                .replaceAll("\\bget_global_size\\(0\\)", "(gridDim.x * blockDim.x)")
                .replaceAll("\\bget_global_size\\(1\\)", "(gridDim.y * blockDim.y)")
                .replaceAll("\\bget_global_size\\(2\\)", "(gridDim.z * blockDim.z)")
                .replaceAll("\\bbarrier\\([^;]*\\)", "__syncthreads()")
                .replace("__builtin_unreachable();", "")
                .replace("__builtin_trap();", "asm(\"trap;\");")
                .replace("restrict", "__restrict__")
                .replaceAll("\\bglobal\\b", "")
                .replaceAll("\\bconstant\\b", "const")
                .replaceAll("\\blocal\\b", "")
                .replaceAll("\\bkernel\\b", "extern \"C\" __global__")
                .replaceAll("\\bCLK_GLOBAL_MEM_FENCE\\b", "0")
                .replaceAll("\\bCLK_LOCAL_MEM_FENCE\\b", "0");
        source = ATTRIBUTE.matcher(source).replaceAll("");
        source = source.replaceAll("\\bnan\\s*\\(\\(uint(?:32|64)_t\\)\\s*[^)]*\\)", "nan(\"\")");
        source = source.replaceAll("\\bnan\\s*\\([^()]*\\)", "nan(\"\")");
        source = COMPOUND_LITERAL.matcher(source).replaceAll("$1{");
        source = POINTER_ASSIGNMENT.matcher(source).replaceAll("$1$2$3 *$4($2$3*)$5;");
        source = source.replace("const const", "const");
        source = source.replace("static const const", "static const");
        source = source.replace("#pragma fp_contract(off)", "");
        return """
                #ifndef DBL_MAX
                #define DBL_MAX 1.7976931348623157e+308
                #endif
                template <typename T, typename L, typename H>
                __device__ auto clamp(T value, L low, H high) {
                    return value < low ? low : (value > high ? high : value);
                }
                __device__ inline short convert_short_sat(long long value) {
                    return (short) clamp(value, -32768LL, 32767LL);
                }
                """ + source;
    }
}
