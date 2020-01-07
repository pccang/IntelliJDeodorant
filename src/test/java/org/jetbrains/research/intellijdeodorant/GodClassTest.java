package org.jetbrains.research.intellijdeodorant;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.research.intellijdeodorant.core.distance.ExtractClassCandidateGroup;
import org.jetbrains.research.intellijdeodorant.core.distance.ExtractClassCandidateRefactoring;
import org.jetbrains.research.intellijdeodorant.core.distance.ProjectInfo;
import org.jetbrains.research.intellijdeodorant.ide.ui.abstractrefactorings.ExtractClassRefactoringType.AbstractExtractClassRefactoring;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;


/**
 * To add a test with the name `TestName`, directory `TestName` should be created in `src/test/resources/testdata/ide/refactoring/godclass/`
 * and three directories should be put inside: `initial`, `expected` and `actual`
 * There should be an initial java file to perform Extract Class Refactoring inside `initial` directory and two (!) files with the expected results inside the `expected` folder
 * (if it is desired to check that there are no Extract Class Refactoring Opportunities for the class, `distance/GodClassDistanceMatrixTest` should be used instead)
 * And, finally, there should be a corresponding method inside this class.
 *
 * It should be noted that the package name should be exactly `TestName`.actual !!!
 * The initial file should be named Test, same for the tested class inside this file, expected directory should contain `Test` and `TestProduct`
 *
 * Expected results can be compared with the Actual ones inside the `actual` directory.
 */
public class GodClassTest extends LightJavaCodeInsightFixtureTestCase {
    private static final String TEST_ROOT = "src/test/resources/testdata/ide/refactoring/godclass/";
    private static final String MOCK_JDK_HOME = "src/test/resources/mockJDK-1.8";
    private ProgressIndicator fakeProgressIndicator = new FakeProgressIndicator();

    public void testSimple() {
        runTest("TestSimple");
    }

    public void testSeparateBlocks() {
        runTest("TestSeparateBlocks", 0);
    }

    public void testSeparateBlocks2() {
        runTest("TestSeparateBlocks2", 1);
    }

    public void testConstructorCreation() {
        runTest("TestConstructorCreation");
    }

    public void testConstructorCreationWithFinalFields() {
        runTest("TestConstructorCreationWithFinalFields");
    }

    public void testSeveralConstructors() {
        runTest("TestSeveralConstructors");
    }

    public void testSourceMemberAccessesInExtractedClass() {
        runTest("TestSourceMemberAccessesInExtractedClass", 1);
    }

    public void testSourceMemberAssigmentsInExtractedClass() {
        runTest("TestSourceMemberAssigmentsesInExtractedClass", 1);
    }

    public void testSimpleInner() {
        runTest("TestSimpleInner", 0);
    }

    public void testInnerClassWithOuterFieldAccess() {
        runTest("TestInnerClassWithOuterFieldAccess", 0);
    }

    public void testCircleDependency() {
        runTest("TestCircleDependency");
    }

    public void testRecursiveCall() {
        runTest("TestRecursiveCall");
    }

    public void testStaticFieldAccess() {
        runTest("TestStaticFieldAccess");
    }

    public void testStaticFieldAccess2() {
        runTest("TestStaticFieldAccess2", 1);
    }

    public void testStaticMethodAccess() {
        runTest("TestStaticMethodAccess");
    }

    public void testStaticMethodAccess2() {
        runTest("TestStaticMethodAccess2", 1);
    }

    private void runTest(String testName) {
        runTest(testName, 0);
    }

    private void runTest(String testName, int candidateNumber) {
        try {
            String testClassName = "Test";
            String testClassProductName = "TestProduct";

            myFixture.copyDirectoryToProject(testName + "/initial", testName + "/actual");

            Set<ExtractClassCandidateGroup> candidateGroups = JDeodorantFacade.getExtractClassRefactoringOpportunities(new ProjectInfo(myFixture.getProject()), fakeProgressIndicator);

            assertTrue(candidateGroups.size() > 0);
            assertTrue(candidateGroups.iterator().hasNext());
            ExtractClassCandidateGroup group = candidateGroups.iterator().next();

            assertTrue(group.getCandidates().size() > candidateNumber);
            ExtractClassCandidateRefactoring candidate = group.getCandidates().get(candidateNumber);

            AbstractExtractClassRefactoring refactoring = new AbstractExtractClassRefactoring(candidate);

            WriteCommandAction.runWriteCommandAction(getProject(), refactoring::apply);

            myFixture.copyDirectoryToProject(testName + "/expected", testName + "/expected");

            saveResult(testName + "/actual/" + testClassName + ".java");
            saveResult(testName + "/actual/" + testClassProductName + ".java");


            VirtualFile mainDirectoryFile = myFixture.findFileInTempDir(testName);
            PsiDirectory mainDirectory = myFixture.getPsiManager().findDirectory(mainDirectoryFile);
            PsiDirectory expectedDirectory = mainDirectory.findSubdirectory("expected");
            PsiDirectory actualDirectory = mainDirectory.findSubdirectory("actual");
            checkDirectoriesAreEqual(actualDirectory, expectedDirectory);
        } catch (com.intellij.psi.PsiInvalidElementAccessException e) {
            e.printStackTrace(System.out);
            System.out.println("\n\n\n");
            System.out.println(e.getAttachments()[0].getDisplayText());
            fail("something invalidates. See stack traces and attachment");
        }
    }

    private void saveResult(String filePath) {
        PsiFile psiFile = myFixture.getPsiManager().findFile(myFixture.findFileInTempDir(filePath));

        filePath = "src/test/resources/testdata/ide/refactoring/godclass/" + filePath;

        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(psiFile.getText());
        } catch (NullPointerException npe) {
            fail("Failed to create extracted file, source file got broken up or the wrong file structure in testdata have been made.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return new ProjectDescriptor(LanguageLevel.JDK_1_8, false) {
            @Override
            public Sdk getSdk() {
                return JavaSdk.getInstance().createJdk("java 1.8", MOCK_JDK_HOME, false);
            }
        };
    }

    @Override
    protected String getTestDataPath() {
        return TEST_ROOT;
    }

    private void checkDirectoriesAreEqual(PsiDirectory result, PsiDirectory expected) {
        for (PsiDirectory directory : result.getSubdirectories()) {
            if (directory.getName().equals("expected")) {
                continue;
            }
            checkDirectoriesAreEqual(directory, expected.findSubdirectory(directory.getName()));
        }

        for (PsiFile file : result.getFiles()) {
            checkFilesAreEqual(file, expected.findFile(file.getName()));
        }
    }

    private void checkFilesAreEqual(PsiFile result, PsiFile expected) {
        String[] resultTokens = result.getText().trim().split("\\s+");
        String[] expectedTokens = expected.getText().trim().split("\\s+");
        assertOrderedEquals(resultTokens, expectedTokens);
    }

    private static class FakeProgressIndicator implements ProgressIndicator {
        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public void setText(String text) {
        }

        @Override
        public String getText() {
            return null;
        }

        @Override
        public void setText2(String text) {

        }

        @Override
        public String getText2() {
            return null;
        }

        @Override
        public double getFraction() {
            return 0;
        }

        @Override
        public void setFraction(double fraction) {
        }

        @Override
        public void pushState() {
        }

        @Override
        public void popState() {
        }

        @Override
        public boolean isModal() {
            return false;
        }

        @NotNull
        @Override
        public ModalityState getModalityState() {
            return null;
        }

        @Override
        public void setModalityProgress(@Nullable ProgressIndicator modalityProgress) {
        }

        @Override
        public boolean isIndeterminate() {
            return false;
        }

        @Override
        public void setIndeterminate(boolean indeterminate) {
        }

        @Override
        public void checkCanceled() throws ProcessCanceledException {
        }

        @Override
        public boolean isPopupWasShown() {
            return false;
        }

        @Override
        public boolean isShowing() {
            return false;
        }
    }
}
