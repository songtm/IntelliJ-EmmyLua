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
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.tang.intellij.lua.codeInsight.template.macro.SuggestTypeMacro
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.TyTable

/**
 *
 * Created by TangZX on 2016/12/16.
 */
class CreateTypeAnnotationIntention : BaseIntentionAction() {

    override fun getFamilyName(): String {
        return text
    }

    override fun getText(): String {
        return "Create type annotation"
    }

    override fun isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
        val localDef = LuaPsiTreeUtil.findElementOfClassAtOffset(psiFile, editor.caretModel.offset, LuaLocalDef::class.java, false)
        val assignStat =  LuaPsiTreeUtil.findElementOfClassAtOffset(psiFile, editor.caretModel.offset, LuaAssignStat::class.java, false)
        var topArrayTable = findTopArrayTable(project, editor, psiFile)
        if (topArrayTable!= null && ( topArrayTable.textOffset > (localDef?:assignStat)!!.textOffset) )
        {
            val pos  = LuaPsiTreeUtil.findElementOfClassAtOffset(psiFile, topArrayTable!!.textOffset, LuaCommentOwner::class.java, false)
            if (pos != null)
            {
                return pos.comment?.tagType == null
            }
        }
        else if (localDef != null) {
            val comment = localDef.comment
            return comment?.tagType == null
        }

        else if (assignStat != null)
        {
            val comment =  assignStat.comment
            return comment?.tagType == null
        }
        return false
    }

    private fun isArrayTable(table:LuaTableExpr):Boolean
    {
        for (tablefield in table.tableFieldList)
        {
            var idExpr = tablefield.idExpr
            if (idExpr is LuaLiteralExpr || (tablefield.id == null && tablefield.idExpr == null))//凡是有[]都当成数组处理
            {
                return true
            }
        }
        return  false
    }
    //从第一层数组开始生成table类型, 不要出现数组与hash混用的结构如{a=1, b=2, [1]={}}
    private fun gentip(table:LuaTableExpr):String {
        var curTabIsArray = isArrayTable(table)

        var tipstr = ""
        if (!curTabIsArray) {
            tipstr += "{"
            table.tableFieldList.forEach { tablefield ->
                val key = tablefield.id?.text
                val expr = tablefield.exprList[0]
                if (expr is LuaTableExpr)
                {
                    tipstr += "$key:"+gentip(expr) + ","
                }else
                {
                    var displayName = expr.guessType(SearchContext.get(expr.project)).displayName
                    if (displayName != null && displayName != "")
                        tipstr += "$key:$displayName,"
                    else
                        tipstr += "$key,"
                }

            }
            tipstr += "}"
            return tipstr
        }else
        {
            var tableFieldList = table.tableFieldList
            for (tablefield in tableFieldList)
            {
                var idExpr = tablefield.idExpr
                if (idExpr is LuaLiteralExpr || (tablefield.id == null && tablefield.idExpr == null))//凡是有[]都当成数组处理
                {
                    var expr = if (tablefield.idExpr == null) tablefield.exprList[0] else tablefield.exprList[1]
                    if (expr is LuaTableExpr)
                    {
                        tipstr += gentip(expr)
                    }
                    else
                    {
                        var displayName = expr.guessType(SearchContext.get(expr.project)).displayName
                        if (displayName != null && displayName != "")
                            tipstr += displayName
                        else
                            tipstr += "{}"
                    }
                    break//目前只处理第一条数组元素, 未考虑数组元素异构的合并处理
                }
            }
            tipstr += "[]"
            return tipstr
        }
    }
    private  fun findTopArrayTable(project: Project, editor: Editor, psiFile: PsiFile):LuaTableExpr?
    {
        var lastArrayTable:LuaTableExpr? = null
        var table = LuaPsiTreeUtil.findElementOfClassAtOffset(psiFile, editor.caretModel.offset, LuaTableExpr::class.java, false)
        while (table != null)
        {
            if (isArrayTable(table))
                lastArrayTable = table
            table = LuaPsiTreeUtil.findElementOfClassAtOffset(psiFile, table.textOffset-1, LuaTableExpr::class.java, false)
        }
        return lastArrayTable
    }
    override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
        val localDef = LuaPsiTreeUtil.findElementOfClassAtOffset(psiFile, editor.caretModel.offset, LuaLocalDef::class.java, false)
        val assignStat =  LuaPsiTreeUtil.findElementOfClassAtOffset(psiFile, editor.caretModel.offset, LuaAssignStat::class.java, false)

        var typename = "table"
        var pos  = (localDef ?: assignStat) as LuaCommentOwner

        var topArrayTable = findTopArrayTable(project, editor, psiFile)
        if (topArrayTable!= null && ( topArrayTable.textOffset > (localDef?:assignStat)!!.textOffset) )
        {
            val tipstr  = gentip(topArrayTable).trim()
            if (tipstr != "") {

                pos  = LuaPsiTreeUtil.findElementOfClassAtOffset(psiFile, topArrayTable!!.textOffset, LuaCommentOwner::class.java, false) ?: pos
                typename = tipstr
            }
        }
        else if (localDef != null || assignStat != null)
        {

            var exprList =  localDef?.exprList ?: assignStat?.valueExprList
            if (exprList!= null)
            {
                var guessType = exprList?.guessType(SearchContext.get(project))
                typename = if (guessType?.displayName != null) guessType.displayName else typename
                if (guessType is TyTable)
                {
                    var table = exprList!!.exprList[0]

                    if(table is LuaTableExpr)
                    {
                        val tipstr  = gentip(table).trim()
                        if (tipstr != "")
                            typename = tipstr
                    }
                }
            }
        }
        LuaCommentUtil.insertTemplate(pos, editor) { _, template ->
            template.addTextSegment("---@type ")
            val name = TextExpression(typename)//MacroCallNode(SuggestTypeMacro())
            template.addVariable("type", name, TextExpression(typename), true)
            template.addEndVariable()
        }
    }
}