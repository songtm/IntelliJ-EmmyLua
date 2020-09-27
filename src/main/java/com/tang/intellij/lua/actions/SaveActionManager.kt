package com.tang.intellij.lua.actions

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiDocumentManager


class SaveActionManager : FileDocumentManagerListener {
    override fun beforeDocumentSaving(document: Document) {
        for (project in ProjectManager.getInstance().openProjects) {
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
            if (psiFile != null) {
                LuaLiveCodingAction.sendCommand(psiFile.virtualFile.path, psiFile.project, psiFile)
                break
            }
        }
    }
}