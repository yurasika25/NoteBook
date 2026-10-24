package com.notes.easynotebook.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FragmentAddNoteArgumentsTest {

    @Test
    fun editNavigationStoresOnlyAnIdentifierAndDraftKey() {
        val arguments = FragmentAddNote.newInstance(42).requireArguments()

        assertEquals(42, arguments.getInt("NOTE_ID_KEY"))
        assertFalse(arguments.containsKey("I_DESK_KEY"))
        assertFalse(arguments.containsKey("I_TITLE_KEY"))
    }

    @Test
    fun recoveryEditorReusesTheChosenDraftKeyWithoutAnExistingNoteId() {
        val arguments = FragmentAddNote.newInstance(recoveryDraftKey = "new_recoverable").requireArguments()

        assertEquals("new_recoverable", arguments.getString("DRAFT_KEY"))
        assertFalse(arguments.containsKey("NOTE_ID_KEY"))
    }
}
