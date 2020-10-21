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

import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.tang.intellij.lua.psi.LuaPsiFile
import java.io.File

class RenameLuaFileProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(psiElement: PsiElement): Boolean {
        return psiElement is LuaPsiFile
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
        val metaFile = File((element as LuaPsiFile).virtualFile.path + ".meta")
        if (metaFile.exists()) {
            metaFile.renameTo(File(metaFile.parent + "/" + newName + ".meta"))
        }
    }
}
