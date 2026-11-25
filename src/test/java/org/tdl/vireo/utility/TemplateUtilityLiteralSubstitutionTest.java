package org.tdl.vireo.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TemplateUtilityLiteralSubstitutionTest {

    private final TemplateUtility templateUtility = new TemplateUtility();

    @Test
    void regexReplacementCharactersAreInsertedLiterally() {
        Map<String, String> values = new HashMap<>();
        values.put("TITLE", "$1 ${x} $&");
        values.put("PATH", "C:\\temp\\$0 \\\\");

        assertEquals("[$1 ${x} $&] [C:\\temp\\$0 \\\\] [$1 ${x} $&]",
            templateUtility.templateLiteral("[{TITLE}] [{PATH}] [{TITLE}]", values));
    }

    @Test
    void insertedValuesAreNotRescanned() {
        Map<String, String> values = new HashMap<>();
        values.put("FAILED_LIST", "{DEPOSITED_LIST}");
        values.put("DEPOSITED_LIST", "deposited");

        assertEquals("{DEPOSITED_LIST}|deposited", templateUtility.templateLiteral("{FAILED_LIST}|{DEPOSITED_LIST}", values));
    }

    @Test
    void unknownPlaceholdersAreLeftAlone() {
        Map<String, String> values = new HashMap<>();
        values.put("KNOWN", "k");

        assertEquals("k {NOT_A_PARAMETER} {lower} {}", templateUtility.templateLiteral("{KNOWN} {NOT_A_PARAMETER} {lower} {}", values));
    }

    @Test
    void nullContentIsNull() {
        assertNull(templateUtility.templateLiteral(null, new HashMap<>()));
    }

    @Test
    void nullValuesLeaveEveryPlaceholder() {
        assertEquals("{A} and {B}", templateUtility.templateLiteral("{A} and {B}", null));
    }

}
