/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.debugger.remote;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.tang.intellij.lua.debugger.LuaDebuggerEvaluator;
import com.tang.intellij.lua.debugger.remote.commands.EvaluatorCommand;
import com.tang.intellij.lua.debugger.remote.commands.GetStackCommand;
import com.tang.intellij.lua.debugger.remote.value.LuaRValue;
import com.tang.intellij.lua.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Created by tangzx on 2016/12/31.
 */
public class LuaMobDebuggerEvaluator extends LuaDebuggerEvaluator {
    private LuaMobDebugProcess process;

    public LuaMobDebuggerEvaluator(@NotNull LuaMobDebugProcess process) {
        this.process = process;
    }

    @Override
    protected void eval(@NotNull String s, @NotNull XEvaluationCallback xEvaluationCallback, @Nullable XSourcePosition xSourcePosition) {
        String ss = s.replace('\n', ' ').trim();
        boolean hasSideEffect = false;
        XDebugSession session = process.getSession();
        if (ss.matches("[\\w\\.\\[\\]\'\"]+")) {
            ss = "return " + ss;
        } else {
            hasSideEffect = true;
            LuaPsiFile file = LuaElementFactory.INSTANCE.createFile(session.getProject(), ss);
            //防止调试时鼠标不小心移到一个超大的table上, 而且单纯的一个tab求值也没有意义,展开也会出bug {a= 1}["a"]语法错
            if (file.getChildren().length == 1 && file.getFirstChild().getChildren().length == 1
                    && file.getFirstChild().getFirstChild() instanceof LuaTableExpr) {
                LuaValue code2 = JsePlatform.standardGlobals().load("local _=\"simple table eval disabled\" return _");
                xEvaluationCallback.evaluated(LuaRValue.Companion.create(s, code2.call(), s, session));
                return;
            }
            if (file.getLastChild() instanceof LuaExprStat) {
                String text = file.getLastChild().getText();
                ss = ss.substring(0, ss.lastIndexOf(text)) + " return " + text;
            }
        }
        boolean topFrameSeled = false;
        if (session instanceof XDebugSessionImpl) {
            topFrameSeled = ((XDebugSessionImpl) session).isTopFrameSelected();
        }
        String stackIndex = "nil";
        XStackFrame frame = session.getCurrentStackFrame();
        if (frame instanceof LuaMobStackFrame) {
            LuaMobStackFrame mobStackFrame  = (LuaMobStackFrame)(frame);
            if (mobStackFrame.getStackLevel() != 1 )
                stackIndex = Integer.toString(mobStackFrame.getStackLevel());
        }

        EvaluatorCommand evaluatorCommand = new EvaluatorCommand(ss, stackIndex, data -> {
            Globals standardGlobals = JsePlatform.standardGlobals();
            LuaValue code = standardGlobals.load(data);
            code = code.call();

            String code2Str = code.get(1).toString();
            LuaValue code2 = standardGlobals.load(String.format("local _=%s return _", code2Str));

            LuaRValue value = LuaRValue.Companion.create(s, code2.call(), s, session);

            xEvaluationCallback.evaluated(value);
        });
        process.runCommand(evaluatorCommand);
        if (xEvaluationCallback.toString().contains("EvaluatingExpressionRootNode") && hasSideEffect && topFrameSeled)
            process.runCommand(new GetStackCommand());//用于刷新Varialbe窗口
    }
}
