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

package com.tang.intellij.lua.editor.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.tree.IElementType
import com.tang.intellij.lua.Constants
import com.tang.intellij.lua.actions.KeyboardState
import com.tang.intellij.lua.comment.psi.impl.LuaDocTagFieldImpl
import com.tang.intellij.lua.psi.LuaClassField
import com.tang.intellij.lua.psi.LuaClassMember
import com.tang.intellij.lua.psi.LuaClassMethodDef
import com.tang.intellij.lua.psi.LuaPsiElement
import com.tang.intellij.lua.refactoring.LuaRefactoringUtil
import com.tang.intellij.lua.ty.IFunSignature
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.ITyFunction
import javax.swing.Icon
import kotlin.math.min

class LookupElementFactory {
    companion object {

        fun createKeyWordLookupElement(keyWordToken: IElementType): LookupElement {
            return LookupElementBuilder.create(keyWordToken).withInsertHandler(KeywordInsertHandler(keyWordToken))
        }

        fun createGuessableLookupElement(name: String, psi: LuaPsiElement, ty: ITy, icon: Icon): LookupElement {
            return LuaTypeGuessableLookupElement(name, psi, ty, false, icon)
        }

        fun createFunctionLookupElement(name: String,
                                        psi: LuaPsiElement,
                                        signature: IFunSignature,
                                        bold: Boolean,
                                        ty: ITyFunction,
                                        icon: Icon): LookupElement {
            val le = TyFunctionLookupElement(name, psi, signature, bold, false, ty, icon)
            le.handler = SignatureInsertHandler(signature)
            return le
        }

        fun createMethodLookupElement(clazzName: String,
                                      lookupString: String,
                                      classMember: LuaClassMember,
                                      signature: IFunSignature,
                                      bold: Boolean,
                                      isColonStyle: Boolean,
                                      fnTy: ITyFunction,
                                      icon: Icon): LuaLookupElement {
            val element = TyFunctionLookupElement(lookupString,
                    classMember,
                    signature,
                    bold,
                    isColonStyle,
                    fnTy,
                    classMember.visibility.warpIcon(icon))

            if (clazzName == Constants.WORD_STRING)
                element.handler = SignatureInsertHandlerForString(signature, isColonStyle)
            else
                element.handler = SignatureInsertHandler(signature, isColonStyle)

            // looks like static
            if (!isColonStyle)
                element.setItemTextUnderlined(true)

            ///////////////cs.cs_xxx注释提示
            var tailTxt = "  [$clazzName]"
            if (classMember is LuaClassMethodDef) {
                if (clazzName == "[global cs]" && lookupString.startsWith("cs_"))
                {
                    tailTxt = ""
                    var comment = classMember.comment
                    var str = comment?.firstChild?.nextSibling?.text
                    if (str != null && str != "@") {
                        tailTxt += (" $str")
                    }
                }
            }
            element.setTailText(tailTxt)
            //////////

            return element
        }

        fun createFieldLookupElement(clazzName: String,
                                     name: String,
                                     field: LuaClassField,
                                     type:ITy?,
                                     bold: Boolean): LuaLookupElement {
            val element = LuaFieldLookupElement(name, field, type, bold)
            if (!name.startsWith("self.") && !LuaRefactoringUtil.isLuaIdentifier(name)) {
                element.lookupString = "['$name']" //不符合变量名规则的 添加括号tab['0abc']
                val baseHandler = element.handler
                element.handler = InsertHandler<LookupElement> { insertionContext, lookupElement ->
                    baseHandler.handleInsert(insertionContext, lookupElement)
                    // remove '.'
                    insertionContext.document.deleteString(insertionContext.startOffset - 1, insertionContext.startOffset)
                }
            }
            else{
                val baseHandler = element.handler
                element.handler = InsertHandler<LookupElement> { context, lookupElement ->
                    baseHandler.handleInsert(context, lookupElement)
                    if (KeyboardState.shiftDown)
                    {
                        var after = name.replace(Regex("""(c_\w+)\d+"""), "\$1") ////self.c_subxxx -> self["c_subxxx"..i]
                        if (after != name) {
                            var start = context.startOffset - 1
                            if (name.startsWith("self.")) {
                                start = context.startOffset + 4
                                after = after.substring(5)
                            }
                            context.document.replaceString(start, context.tailOffset, "[\"$after\" .. i]")
                            context.editor.caretModel.currentCaret.setSelection(context.tailOffset - 2, context.tailOffset - 1)
                            context.editor.caretModel.currentCaret.moveToOffset(context.tailOffset - 1)
                        }
                    }
                }
            }

            //---@field 注释提示
            if (field is LuaDocTagFieldImpl)
            {
                val comment  = field.commentString?.string?.text ?: ""
                element.setTailText("  [${clazzName}]  "+comment)
            }
            else
            {
                element.setTailText("  [${clazzName}]")
            }

            return element
        }
    }
}