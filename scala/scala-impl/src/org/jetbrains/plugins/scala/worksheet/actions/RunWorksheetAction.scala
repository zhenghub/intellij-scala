package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.execution._
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.process.{OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.ide.scratch.{ScratchFileService, ScratchRootType}
import com.intellij.ide.util.EditorHelper
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.keymap.{KeymapManager, KeymapUtil}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdkType, JdkUtil}
import com.intellij.openapi.roots.{ModuleRootManager, ProjectFileIndex}
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileWithId}
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaUtil
import worksheet.processor.WorksheetCompiler
import worksheet.runconfiguration.WorksheetCache
import worksheet.server.WorksheetProcessManager
import worksheet.ui.WorksheetEditorPrinterFactory

/**
 * @author Ksenia.Sautina
 * @author Dmitry Naydanov        
 * @since 10/17/12
 */

class RunWorksheetAction extends AnAction with TopComponentAction {
  def actionPerformed(e: AnActionEvent) {
    RunWorksheetAction.runCompiler(e.getProject, auto = false)
  }

  override def update(e: AnActionEvent) {
    super.update(e)
    
    val shortcuts = KeymapManager.getInstance.getActiveKeymap.getShortcuts("Scala.RunWorksheet")
    
    if (shortcuts.nonEmpty) {
      val shortcutText = " (" + KeymapUtil.getShortcutText(shortcuts(0)) + ")"
      e.getPresentation.setText(ScalaBundle.message("worksheet.execute.button") + shortcutText)
    }
  }

  override def actionIcon = AllIcons.Actions.Execute

  override def bundleKey = "worksheet.execute.button"

  override def shortcutId: Option[String] = Some("Scala.RunWorksheet")
}

object RunWorksheetAction {
  private val runnerClassName = "org.jetbrains.plugins.scala.worksheet.MyWorksheetRunner"

  def runCompiler(project: Project, auto: Boolean) {
    UsageTrigger.trigger("scala.worksheet")

    if (project == null) return

    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor

    if (editor == null) return

    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    WorksheetProcessManager.stop(psiFile.getVirtualFile)

    psiFile match {
      case file: ScalaFile if file.isWorksheetFile =>
        val viewer = WorksheetCache.getInstance(project) getViewer editor

        if (viewer != null && !WorksheetCompiler.isWorksheetReplMode(file)) { 
          ApplicationManager.getApplication.invokeAndWait(new Runnable {
            override def run() {
              scala.extensions.inWriteAction {
                CleanWorksheetAction.resetScrollModel(viewer)
                if (!auto) CleanWorksheetAction.cleanWorksheet(file.getNode, editor, viewer, project)
              }
            }
          }, ModalityState.any())
        }

        def runnable() = {
          new WorksheetCompiler(editor, file, (className: String, addToCp: String) => {
            ApplicationManager.getApplication invokeLater new Runnable {
              override def run() {
                executeWorksheet(file.getName, project, file.getContainingFile, className, addToCp)
              }
            }
          }, auto).compileAndRun()
        }

        if (WorksheetCompiler isMakeBeforeRun psiFile) {
          CompilerManager.getInstance(project).make(
            getModuleFor(file),
            new CompileStatusNotification {
              override def finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
                if (!aborted && errors == 0) runnable()
              }
            })
        } else runnable()
      case _ =>
    }
  }

  //FYI: repl mode works only if we use compiler server and run worksheet with "InProcess" setting
  def executeWorksheet(name: String, project: Project, file: PsiFile, mainClassName: String, addToCp: String) {
    val virtualFile = file.getVirtualFile
    val params = createParameters(getModuleFor(file), mainClassName, Option(project.getBaseDir) map (_.getPath) getOrElse "", addToCp, "",
      virtualFile.getCanonicalPath) //todo extract default java options??

    setUpUiAndRun(params.createOSProcessHandler(), file)
  }

  private def createParameters(module: Module, mainClassName: String,
                               workingDirectory: String, additionalCp: String, consoleArgs: String, worksheetField: String) =  {
    import _root_.scala.collection.JavaConverters._

    if (module == null) throw new ExecutionException("Module is not specified")

    val project = module.getProject

    val scalaSdk = module.scalaSdk.getOrElse {
      throw new ExecutionException("No Scala facet configured for module " + module.getName)
    }

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = rootManager.getSdk
    if (sdk == null || !sdk.getSdkType.isInstanceOf[JavaSdkType]) {
      throw CantRunException.noJdkForModule(module)
    }

    val params = new JavaParameters()
    val files = scalaSdk.compilerClasspath

    params.getClassPath.addAllFiles(files.asJava)
    params.setUseDynamicClasspath(JdkUtil.useDynamicClasspath(project))
    params.getClassPath.add(ScalaUtil.runnersPath())
    params.setWorkingDirectory(workingDirectory)
    params.setMainClass(runnerClassName)
    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)

    params.getClassPath.add(ScalaUtil.runnersPath())
    params.getClassPath.add(additionalCp)
    params.getProgramParametersList addParametersString worksheetField
    if (!consoleArgs.isEmpty) params.getProgramParametersList addParametersString consoleArgs
    params.getProgramParametersList prepend mainClassName //IMPORTANT! this must be first program argument

    params
  }

  private def setUpUiAndRun(handler: OSProcessHandler, file: PsiFile) {
    val editor = EditorHelper openInEditor file
    
    val scalaFile = file match {
      case sc: ScalaFile => sc
      case _ => return 
    }

    val worksheetPrinter =  WorksheetEditorPrinterFactory.newWorksheetUiFor(editor, scalaFile, isRepl = false)

    val myProcessListener: ProcessAdapter = new ProcessAdapter {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
        val text = event.getText
        if (ConsoleViewContentType.NORMAL_OUTPUT == (ConsoleViewContentType getConsoleViewType outputType)) 
          worksheetPrinter processLine text
      }

      override def processTerminated(event: ProcessEvent): Unit = {
        worksheetPrinter.flushBuffer()
      }
    }

    handler.addProcessListener(myProcessListener)
    handler.startNotify()
  }
  
  def isScratchWorksheet(vFileOpt: Option[VirtualFile], project: Project): Boolean = vFileOpt.exists {
    vFile => ScratchFileService.getInstance().getRootType(vFile).isInstanceOf[ScratchRootType] &&
      ScalaProjectSettings.getInstance(project).isTreatScratchFilesAsWorksheet
  }  
  
  def isScratchWorksheet(file: PsiFile): Boolean = isScratchWorksheet(Option(file.getVirtualFile), file.getProject)

  def getModuleFor(vFile: VirtualFile, project: Project): Module = {
    vFile match {
      case _: VirtualFileWithId =>
        Option(ProjectFileIndex.SERVICE getInstance project getModuleForFile
          vFile) getOrElse project.anyScalaModule.map(_.module).orNull
      case _ => project.anyScalaModule.map(_.module).orNull
    }
  }
  
  def getModuleFor(file: PsiFile): Module = WorksheetCompiler.getModuleForCpName(file) flatMap {
    name =>
      scala.extensions.inReadAction {
        Option(ModuleManager getInstance file.getProject findModuleByName name)
      }
  } getOrElse getModuleFor(file.getVirtualFile, file.getProject)
}