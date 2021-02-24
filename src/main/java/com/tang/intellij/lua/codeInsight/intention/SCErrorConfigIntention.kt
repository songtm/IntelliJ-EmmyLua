package com.tang.intellij.lua.codeInsight.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.psi.LuaDocTagField
import com.tang.intellij.lua.project.LuaSettings
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassIndex
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex

class SCErrorConfigIntention : BaseIntentionAction() {
    var classname = ""
    override fun getFamilyName() = "Gen sc error config"

    override fun getText() = familyName

    override fun isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
        val resultField = getResultField(project, editor, psiFile)
        return resultField != null && (resultField is LuaDocTagField)
    }

    private fun getResultField(project: Project, editor: Editor, psiFile: PsiFile): LuaClassMember? {
        val offset = editor.caretModel.offset
        var element = psiFile.findElementAt(offset)
        var text = if (element?.text != null) element.text else ""
        text = text.trim('"', '\'')
        classname = text
        if (text.length > 4 && text.substring(0, 3).toLowerCase() == "sc_") {
            val searchContext = SearchContext.get(project)
            val classDef = LuaClassIndex.find(text, searchContext)
            if (classDef != null) {
                return LuaClassMemberIndex.find(classDef.type, "result", searchContext)
            }
        }
        return null
    }

    private fun resolveSCErrorFile(classname: String?, project: Project): PsiElement? {
        if (classname == null)
            return null
        val fileName = "data_sc_error.lua"//LuaSettings.instance.scErrorFile
        val f = LuaFileUtil.findFile(project, fileName)
        if (f != null) {
            val psiFile = PsiManager.getInstance(project).findFile(f)
            if (psiFile is LuaPsiFile) {
                var localDef = PsiTreeUtil.findChildOfType(psiFile, LuaLocalDef::class.java)
                var firstChild = localDef?.exprList?.firstChild
                if (firstChild is LuaTableExpr) {
                    var list = PsiTreeUtil.getChildrenOfTypeAsList(firstChild, LuaTableField::class.java)
                    list.forEach { fld ->
                        if (fld.idExpr?.text == classname)
                            return fld
                    }
                    return firstChild
                }

            }
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
        val resultField = getResultField(project, editor, psiFile)
        if (resultField is LuaDocTagField) {
            val commentString = resultField.commentString?.text ?: ""
//            val r = Regex("\\d{3}-(\\d{4})-\\d{2}")
            var r = Regex("(\\d+)\\s*:=\\s*([^0-9]+)")
            var m = r.findAll(commentString)
            if (m.count() <= 0) {
                r = Regex("(\\d+)\\s*-\\s*([^0-9]+)")
                m = r.findAll(commentString)
            }
            var treestr = "[${classname.toUpperCase()}] = {"
            m.forEach {
                val id = it.groupValues.get(1)
                val str = it.groupValues.get(2)
                treestr += "\n\t[$id] = \"$str\","
            }
            treestr += "\n},"
            treestr = "local a = {\n$treestr\n}"
            println(treestr)

            var dstTree: LuaTableField? = null
            val localDef = LuaElementFactory.createWith(project, treestr) as LuaLocalDef
            var firstChild = localDef?.exprList?.firstChild
            if (firstChild is LuaTableExpr) {
                var list = PsiTreeUtil.getChildrenOfTypeAsList(firstChild, LuaTableField::class.java)
                dstTree = list[0]
//                tab.parent.replace(tmp)
            }
            val tab = resolveSCErrorFile(classname.toUpperCase(), project)
            if (tab != null && tab is LuaTableField) { //协议已经存在
                var orgdic = (tab.exprList.last() as LuaTableExpr).tableFieldList
                var curdic = (dstTree?.exprList?.last() as LuaTableExpr).tableFieldList
                curdic.forEach { it ->
                    for (org in orgdic) {
                        if (org.idExpr?.text == it.idExpr?.text)
                            it.replace(org)
                    }
                }
                tab.replace(dstTree!!)
            } else if (tab != null && tab is LuaTableExpr)//local a = {xxx}
            {
                tab.addRangeAfter(dstTree!!.prevSibling, dstTree.nextSibling, tab.tableFieldSepList[0])
            }

            var newpos = resolveSCErrorFile(classname.toUpperCase(), project)

            if (newpos is Navigatable && newpos.canNavigate()) {
                newpos.navigate(true)
            }

        }

//        val callExpr = LuaPsiTreeUtil.findElementOfClassAtOffset(psiFile, editor.caretModel.offset, LuaCallExpr::class.java, false)
//                ?: return
//        val code = "${callExpr.expr.text}(${callExpr.args.text})"
//        val file = LuaElementFactory.createFile(project, code)
//        val newCall = PsiTreeUtil.findChildOfType(file, LuaCallExpr::class.java) ?: return
//        callExpr.replace(newCall)
    }
}