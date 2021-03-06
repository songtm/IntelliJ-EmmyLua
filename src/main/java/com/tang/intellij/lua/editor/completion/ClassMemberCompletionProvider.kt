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

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.impl.LuaNameExprImpl
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

enum class MemberCompletionMode {
    Dot,    // self.xxx
    Colon,  // self:xxx()
    All     // self.xxx && self:xxx()
}

/**

 * Created by tangzx on 2016/12/25.
 */
open class ClassMemberCompletionProvider : LuaCompletionProvider() {
    protected abstract class HandlerProcessor {
        open fun processLookupString(lookupString: String, member: LuaClassMember, memberTy: ITy?): String = lookupString
        abstract fun process(element: LuaLookupElement, member: LuaClassMember, memberTy: ITy?): LookupElement
    }

    override fun addCompletions(session: CompletionSession) {
        val completionParameters = session.parameters
        val completionResultSet = session.resultSet

        val psi = completionParameters.position
        val indexExpr = psi.parent

        if (indexExpr is LuaIndexExpr) {
            val isColon = indexExpr.colon != null
            val project = indexExpr.project
            val contextTy = LuaPsiTreeUtil.findContextClass(indexExpr.realContext)
            val context = SearchContext.get(project)
            val prefixType = indexExpr.guessParentType(context)

            ////songtm
            if (prefixType is TyClass) {
                val superClass = prefixType.getSuperClass(context)
                val isNameExpr  = indexExpr.firstChild is LuaNameExprImpl
                if (isNameExpr && (indexExpr.firstChild as LuaNameExprImpl).name == "self" && superClass != null)
                {
                    val ele = LookupElementBuilder.create(LuaSettings.instance.superRefName).withTailText("  [super class]")
                            .withIcon(LuaIcons.CLASS).withTypeText(superClass.displayName)
                    completionResultSet.addElement(ele)
                }
                else if (isNameExpr && (indexExpr.firstChild as LuaNameExprImpl).name == prefixType.displayName)
                {
                    val replaceStr = "._.ctor()"
                    val txt = prefixType.displayName + replaceStr
                    val ele = LookupElementBuilder.create("new").withTailText("  $txt")
                            .withIcon(LuaIcons.CLASS).withTypeText(prefixType.displayName)
                            .withInsertHandler(InsertHandler<LookupElement> { insertionContext, lookupElement ->
                                val startOffset = insertionContext.startOffset
                                val element = insertionContext.file.findElementAt(startOffset)
                                val editor = insertionContext.editor
                                insertionContext.document.replaceString(startOffset - 1, startOffset + 3, replaceStr)
                                editor.caretModel.moveToOffset(startOffset + replaceStr.length - 2)
                                AutoPopupController.getInstance(insertionContext.project).autoPopupParameterInfo(editor, element)
                            })

                    completionResultSet.addElement(ele)
                }
            }
            ////

            if (!Ty.isInvalid(prefixType)) {
                complete(isColon, project, contextTy, prefixType, completionResultSet, completionResultSet.prefixMatcher, null)
            }
            //smart
            val nameExpr = indexExpr.prefixExpr
            if (false && (nameExpr is LuaNameExpr)) {
                val colon = if (isColon) ":" else "."
                val prefixName = nameExpr.text
                val postfixName = indexExpr.name?.let { it.substring(0, it.indexOf(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)) }

                val matcher = completionResultSet.prefixMatcher.cloneWithPrefix(prefixName)
                LuaDeclarationTree.get(indexExpr.containingFile).walkUpLocal(indexExpr) { d ->
                    val it = d.firstDeclaration.psi
                    val txt = it.name
                    if (it is LuaTypeGuessable && txt != null && prefixName != txt && matcher.prefixMatches(txt)) {
                        val type = it.guessType(context)
                        if (!Ty.isInvalid(prefixType)) {
                            val prefixMatcher = completionResultSet.prefixMatcher
                            val resultSet = completionResultSet.withPrefixMatcher("$prefixName*$postfixName")
                            complete(isColon, project, contextTy, type, resultSet, prefixMatcher, object : HandlerProcessor() {
                                override fun process(element: LuaLookupElement, member: LuaClassMember, memberTy: ITy?): LookupElement {
                                    element.itemText = txt + colon + element.itemText
                                    element.lookupString = txt + colon + element.lookupString
                                    return PrioritizedLookupElement.withPriority(element, -2.0)
                                }
                            })
                        }
                    }
                    true
                }
            }
        }
    }

    private fun complete(isColon: Boolean,
                         project: Project,
                         contextTy: ITy,
                         prefixType: ITy,
                         completionResultSet: CompletionResultSet,
                         prefixMatcher: PrefixMatcher,
                         handlerProcessor: HandlerProcessor?) {
        val mode = if (isColon) MemberCompletionMode.Colon else MemberCompletionMode.Dot
        prefixType.eachTopClass(Processor { luaType ->
            addClass(contextTy, luaType, project, mode, completionResultSet, prefixMatcher, handlerProcessor)
            true
        })
    }

    protected fun addClass(contextTy: ITy,
                           luaType:ITyClass,
                           project: Project,
                           completionMode:MemberCompletionMode,
                           completionResultSet: CompletionResultSet,
                           prefixMatcher: PrefixMatcher,
                           handlerProcessor: HandlerProcessor?) {
        val context = SearchContext.get(project)
        luaType.lazyInit(context)
        luaType.processMembers(context) { curType, member ->
            ProgressManager.checkCanceled()
            member.name?.let {
                if (prefixMatcher.prefixMatches(it) && curType.isVisibleInScope(project, contextTy, member.visibility)) {
                    addMember(completionResultSet,
                            member,
                            curType,
                            luaType,
                            completionMode,
                            project,
                            handlerProcessor)
                }
            }
        }
    }

    protected fun addMember(completionResultSet: CompletionResultSet,
                            member: LuaClassMember,
                            thisType: ITyClass,
                            callType: ITyClass,
                            completionMode: MemberCompletionMode,
                            project: Project,
                            handlerProcessor: HandlerProcessor?) {
        val type = member.guessType(SearchContext.get(project))
        val bold = thisType == callType
        val className = thisType.displayName
        if (type is ITyFunction) {
            val fn = type.substitute(TySelfSubstitutor(project, null, callType))
            if (fn is ITyFunction)
                addFunction(completionResultSet, bold, completionMode != MemberCompletionMode.Dot, className, member, fn, thisType, callType, handlerProcessor)
        } else if (member is LuaClassField) {
            if (completionMode != MemberCompletionMode.Colon)
                addField(completionResultSet, bold, className, member, type, handlerProcessor)
        }
    }

    protected fun addField(completionResultSet: CompletionResultSet,
                           bold: Boolean,
                           clazzName: String,
                           field: LuaClassField,
                           ty:ITy?,
                           handlerProcessor: HandlerProcessor?) {
        var name = field.name
        if (name != null) {
            name = handlerProcessor?.processLookupString(name, field, ty) ?: name
            val element = LookupElementFactory.createFieldLookupElement(clazzName, name, field, ty, bold)
            val ele = handlerProcessor?.process(element, field, null) ?: element
            completionResultSet.addElement(ele)
        }
    }

    private fun addFunction(completionResultSet: CompletionResultSet,
                            bold: Boolean,
                            isColonStyle: Boolean,
                            clazzName: String,
                            classMember: LuaClassMember,
                            fnTy: ITyFunction,
                            thisType: ITyClass,
                            callType: ITyClass,
                            handlerProcessor: HandlerProcessor?) {
        val name = classMember.name
        if (name != null) {
            fnTy.process(Processor {

                val firstParam = it.getFirstParam(thisType, isColonStyle)
                if (isColonStyle) {
                    if (firstParam == null) return@Processor true
                    if (!callType.subTypeOf(firstParam.ty, SearchContext.get(classMember.project), true))
                        return@Processor true
                }

                val lookupString = handlerProcessor?.processLookupString(name, classMember, fnTy) ?: name

                var replaceDot = (!isColonStyle)&&it.colonCall && LuaSettings.instance.dotAsColon
                var realColonStyle = if (replaceDot) true else isColonStyle

                val element = LookupElementFactory.createMethodLookupElement(clazzName,
                        lookupString,
                        classMember,
                        it,
                        bold,
                        realColonStyle,
                        fnTy,
                        LuaIcons.CLASS_METHOD)

                if ((thisType.className == "cs") && (lookupString.startsWith("cs_"))) {
                    element.handler = CSSignatureInsertHandler(it, isColonStyle)
                }
                else if (element.handler is SignatureInsertHandler)
                    (element.handler as SignatureInsertHandler).replaceDot = replaceDot


                val ele = handlerProcessor?.process(element, classMember, fnTy) ?: element
                completionResultSet.addElement(ele)
                true
            })
        }
    }
}
