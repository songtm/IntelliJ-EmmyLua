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

package com.tang.intellij.lua.codeInsight.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.comment.psi.LuaDocTagReturn
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.impl.LuaClassMethodDefImpl
import com.tang.intellij.lua.psi.impl.LuaClassMethodNameImpl
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.TyClass
import com.tang.intellij.lua.ty.TyDocTable
import com.tang.intellij.lua.ty.TySerializedDocTable

class CreateFileFuncReturnAnnoIntention : BaseIntentionAction() {
    override fun getFamilyName() = "Create return annotation 4 class"
    override fun getText() = familyName

    override fun isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
        return psiFile.findElementAt(editor.caretModel.offset)?.parent?.parent is LuaClassMethodNameImpl
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
        val method = psiFile.findElementAt(editor.caretModel.offset)?.parent?.parent?.parent as LuaClassMethodDefImpl
        val cls = method.guessClassType(SearchContext.get(project))

//        val cls = (psiFile.findElementAt(editor.caretModel.offset)?.parent as LuaDocTagClass).type
        cls?.processMembers(SearchContext.get(project), {itycls, luaclssmember ->
            if (luaclssmember is LuaClassMethodDef)
            {
                if (luaclssmember is LuaCommentOwner) {
                    val comment = luaclssmember.comment
                    val willGen =  comment == null || PsiTreeUtil.getChildrenOfType(comment, LuaDocTagReturn::class.java) == null
                    if (willGen)
                        genReturnDoc(luaclssmember, editor)
                }
            }
        }, false)
    }
    private fun genReturnDoc(bodyOwner: LuaClassMethodDef, editor: Editor)
    {
        var ty = bodyOwner.guessReturnType(SearchContext.get(editor.project!!))
        if (ty is TyClass && ty !is TyDocTable && ty !is TySerializedDocTable)
        {
            var rtype: String = ty.displayName
            LuaCommentUtil.insertTemplate(bodyOwner, editor) { _, template ->
                template.addTextSegment("---@return ")
                val typeSuggest = TextExpression(rtype) //MacroCallNode(SuggestTypeMacro())
                template.addSelectionStartVariable()
                template.addVariable("returnType", typeSuggest, TextExpression(rtype), false)
                template.addSelectionEndVariable()
                template.addEndVariable()
            }
        }

    }
}