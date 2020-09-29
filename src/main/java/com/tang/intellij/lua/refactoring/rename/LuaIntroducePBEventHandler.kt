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

package com.tang.intellij.lua.refactoring.rename

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.refactoring.LuaRefactoringUtil
import java.nio.file.OpenOption
import kotlin.reflect.jvm.internal.impl.utils.SmartList

/**
 *
 * Created by songtm on 2018/7/3.
 */
class LuaIntroducePBEventHandler : RefactoringActionHandler {

    internal inner class IntroduceOperation(val element: PsiElement, val project: Project, val editor: Editor, val file: PsiFile) {
        var isReplaceAll: Boolean = false
        var occurrences: List<PsiElement>? = null
        var name = "var"
        var newOccurrences: List<PsiElement>? = null
        var newNameElement: LuaNameDef? = null
    }

    override fun invoke(project: Project, editor: Editor, psiFile: PsiFile, dataContext: DataContext) {

    }

    override fun invoke(project: Project, psiElements: Array<PsiElement>, dataContext: DataContext) {

    }

    operator fun invoke(project: Project, editor: Editor, expr: LuaExpr, varName: String) {
        val operation = IntroduceOperation(expr, project, editor, expr.containingFile)
        operation.name = varName
        operation.occurrences = getOccurrences(expr)
        WriteCommandAction.runWriteCommandAction(operation.project) { performReplace(operation) }
//        OccurrencesChooser.simpleChooser<PsiElement>(editor).showChooser(expr, operation.occurrences!!, object : Pass<OccurrencesChooser.ReplaceChoice>() {
//            override fun pass(choice: OccurrencesChooser.ReplaceChoice) {
//                operation.isReplaceAll = choice == OccurrencesChooser.ReplaceChoice.ALL
//                WriteCommandAction.runWriteCommandAction(operation.project) { performReplace(operation) }
////                performInplaceIntroduce(operation)
//            }
//        })
    }


    operator fun invoke(project: Project, editor: Editor, expr: LuaExpr, varName: String, getfuncName:String) {
        val operation = IntroduceOperation(expr, project, editor, expr.containingFile)
        operation.name = getfuncName
        operation.occurrences = getOccurrences(expr)
//        WriteCommandAction.runWriteCommandAction(operation.project) { performGenGetter(operation) }

        var document = operation.editor.document
        val element = operation.element



        val context = PsiTreeUtil.findFirstParent(element, { it is LuaClassMethodDef })
        val clsdef = context as? LuaClassMethodDef
        if (clsdef != null) {
//            document.insertString(clsdef.nextSibling.textOffset, "------")

            document.insertString(clsdef.nextSibling.textOffset,
                    "\n\nfunction " + clsdef.classMethodName.expr.text + ":" + getfuncName + "() return "+element.text+" end")
        }

        document.deleteString(element.parent.textOffset, element.parent.textOffset+element.text.length)

        val manager = TemplateManager.getInstance(operation.project)
        val template = manager.createTemplate("", "") //key, group
        template.addTextSegment(operation.element.text + " = ")
        template.addVariable("initvalue", TextExpression("nil"), true)
        template.addTextSegment(" ---@type ")
        template.addVariable("varType", TextExpression("void"), true)
        manager.startTemplate(operation.editor, template)


    }



    private fun performGenGetter(operation: IntroduceOperation) {
        if (!operation.isReplaceAll)
            operation.occurrences = listOf(operation.element)

        var commonParent = PsiTreeUtil.findCommonParent(operation.occurrences!!)//class method
        if (commonParent != null) {
            var element = operation.element
            var elementText = element.text
            var eleOffset = element.textOffset
            val context = PsiTreeUtil.findFirstParent(element, { it is LuaClassMethodDef })
            val clsdef = context as? LuaClassMethodDef
            if (clsdef != null) {
                var funName = operation.name
                var callbackFun = LuaElementFactory.createWith(operation.project,
                        "\n\nfunction " + clsdef.classMethodName.expr.text + ":" + funName + "() return "+elementText+" end")
                clsdef.parent.addAfter(callbackFun.parent, clsdef)//这里的报错可以用addRangeAfter(ballbackFun.parent.first...)处理,但是有个问题是格式化代码的问题
            }

            var regCall = LuaElementFactory.createWith(operation.project, elementText + " = nil ---@type void\n")
            regCall = element.parent.replace(regCall)
            operation.editor.caretModel.moveToOffset(eleOffset + elementText.length + " = nil".length - "self.".length)
        }
    }

    private fun getOccurrences(expr: LuaExpr): List<PsiElement> {
        return LuaRefactoringUtil.getOccurrences(expr, expr.containingFile)
    }

    private fun findAnchor(occurrences: List<PsiElement>?): PsiElement? {
        var anchor = occurrences!![0]
        next@ do {
            val statement = PsiTreeUtil.getParentOfType(anchor, LuaStatement::class.java)
            if (statement != null) {
                val parent = statement.parent
                for (element in occurrences) {
                    if (!PsiTreeUtil.isAncestor(parent, element, true)) {
                        anchor = statement
                        continue@next
                    }
                }
            }
            return statement
        } while (true)
    }

    private fun isInline(commonParent: PsiElement, operation: IntroduceOperation): Boolean {
        var parent = commonParent
        if (parent === operation.element)
            parent = operation.element.parent
        return parent is LuaStatement && (!operation.isReplaceAll || operation.occurrences!!.size == 1)
    }

    private fun performReplace(operation: IntroduceOperation) {
        if (!operation.isReplaceAll)
            operation.occurrences = listOf(operation.element)

        var commonParent = PsiTreeUtil.findCommonParent(operation.occurrences!!)//class method
        if (commonParent != null) {
            var element = operation.element
            val newOccurrences = SmartList<PsiElement>()
            val context = PsiTreeUtil.findFirstParent(element, { it is LuaClassMethodDef })
            val clsdef = context as? LuaClassMethodDef
            if (clsdef != null) {
                var comment = "\n---@param data "+element.text.toLowerCase()
                var callbackFun = LuaElementFactory.createWith(operation.project,
                        comment+"\nfunction " + clsdef.classMethodName.expr.text + ":" + element.text.toLowerCase() + "(id, data)\n\tdumpx(data)\nend")
                clsdef.parent.addAfter(callbackFun.parent, clsdef)
            }

            var regCall = LuaElementFactory.createWith(operation.project, "PROTO(" + element.text + ", self, self." + element.text.toLowerCase() + ")")
            regCall = element.parent.replace(regCall)

//            val nameDef = PsiTreeUtil.findChildOfType(regCall, LuaNameDef::class.java)!!
//            operation.editor.caretModel.moveToOffset(nameDef.textOffset)



//            if (isInline(commonParent, operation)) {
//                if (element is LuaCallExpr && element.parent is LuaExprStat) element = element.parent
//
//                regCall = element.replace(regCall)
//                val nameDef = PsiTreeUtil.findChildOfType(regCall, LuaNameDef::class.java)!!
//                operation.editor.caretModel.moveToOffset(nameDef.textOffset)
//            } else {
//                val anchor = findAnchor(operation.occurrences)
//                commonParent = anchor!!.parent
//                regCall = commonParent!!.addBefore(regCall, anchor)
//                commonParent.addAfter(LuaElementFactory.newLine(operation.project), regCall)
//                for (occ in operation.occurrences!!) {
//                    var identifier = LuaElementFactory.createName(operation.project, operation.name)
//                    identifier = occ.replace(identifier)
//                    newOccurrences.add(identifier)
//                }
//            }
//
//            operation.newOccurrences = newOccurrences
//            regCall = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(regCall)
//            val nameDef = PsiTreeUtil.findChildOfType(regCall, LuaNameDef::class.java)
//            operation.newNameElement = nameDef
        }
    }

    private fun performInplaceIntroduce(operation: IntroduceOperation) {
        LuaIntroduce(operation).performInplaceRefactoring(null)
    }

    private inner class LuaIntroduce internal constructor(operation: IntroduceOperation)
        : InplaceVariableIntroducer<PsiElement>(operation.newNameElement, operation.editor, operation.project, "Introduce Variable", operation.newOccurrences?.toTypedArray(), null) {

        override fun checkLocalScope(): PsiElement? {
            val currentFile = PsiDocumentManager.getInstance(this.myProject).getPsiFile(this.myEditor.document)
            return currentFile ?: super.checkLocalScope()
        }
    }
}
