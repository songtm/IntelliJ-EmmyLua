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

package com.tang.intellij.lua.codeInsight.postfix.templates;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.tang.intellij.lua.psi.LuaExpr;
import com.tang.intellij.lua.refactoring.rename.LuaIntroducePBEventHandler;
import com.tang.intellij.lua.refactoring.rename.LuaIntroduceVarHandler;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tang.intellij.lua.codeInsight.postfix.LuaPostfixUtils.selectorAllExpressionsWithCurrentOffset;

/**
 * post fix test
 * Created by tangzx on 2017/2/4.
 */
public class LuaLocalPostfixTemplate extends PostfixTemplateWithExpressionSelector {

    public LuaLocalPostfixTemplate() {
        super("var", "local inst = expr", selectorAllExpressionsWithCurrentOffset());
    }

    @Override
    protected void expandForChooseExpression(@NotNull PsiElement psiElement, @NotNull Editor editor) {
        String text = psiElement.getNode().getText();

        if (text.matches("self\\.m_\\w+$")){
            LuaIntroducePBEventHandler handler = new LuaIntroducePBEventHandler();
            String funcname = text.substring(7, 8).toUpperCase() + text.substring(8, text.length());
            handler.invoke(editor.getProject(), editor, (LuaExpr) psiElement, "var", "get"+funcname);
        }
//        else if (text.matches("self\\.c_\\w+\\d$")){ //self.c_subxxx -> self["c_subxxx"..i]
//            Document document = editor.getDocument();
//            int offset = psiElement.getTextOffset();
//            String newStr = "[\""+text.substring(5, text.length() - 1)+"\" .. ]";
//            document.replaceString(offset - 1, offset + text.length()-5, newStr);
//            editor.getCaretModel().moveToOffset(offset + newStr.length() - 2);
//        }
        else if (text.length() > 4 && text.substring(0, 3).equals("SC_") ) {
            LuaIntroducePBEventHandler handler = new LuaIntroducePBEventHandler();
            handler.invoke(editor.getProject(), editor, (LuaExpr) psiElement, "var");
        }
        else
        {
            LuaIntroduceVarHandler handler = new LuaIntroduceVarHandler();
            String varName = "var";
            if (text.length() > 7 && text.substring(0, 7).equals("require") ) {
                String pattern = "require\\(.*\\.(\\w+)";//require("xxx.xx.xx")
                Pattern r = Pattern.compile(pattern);
                Matcher m = r.matcher(text);
                if (m.find()) {
                    varName = m.group(1);
                }
            }
            handler.invoke(editor.getProject(), editor, (LuaExpr) psiElement, varName);
        }

    }
}
