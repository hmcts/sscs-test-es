package uk.gov.hmcts.reform.sscs.callback.handlers;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.hmcts.reform.sscs.callback.handlers.HandlerHelper.buildTestCallbackForGivenData;
import static uk.gov.hmcts.reform.sscs.ccd.callback.CallbackType.*;
import static uk.gov.hmcts.reform.sscs.ccd.domain.EventType.*;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.READY_TO_LIST;
import static uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils.YES;
import static uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils.buildCaseData;
import static uk.gov.hmcts.reform.sscs.domain.FurtherEvidenceLetterType.APPELLANT_LETTER;
import static uk.gov.hmcts.reform.sscs.domain.FurtherEvidenceLetterType.JOINT_PARTY_LETTER;
import static uk.gov.hmcts.reform.sscs.service.placeholders.PlaceholderHelper.buildJointParty;
import static uk.gov.hmcts.reform.sscs.service.placeholders.PlaceholderHelper.buildOtherParty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.sscs.ccd.callback.Callback;
import uk.gov.hmcts.reform.sscs.ccd.callback.DocumentType;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.config.DocmosisTemplateConfig;
import uk.gov.hmcts.reform.sscs.domain.FurtherEvidenceLetterType;
import uk.gov.hmcts.reform.sscs.service.BulkPrintService;
import uk.gov.hmcts.reform.sscs.service.CoverLetterService;
import uk.gov.hmcts.reform.sscs.service.PdfStoreService;
import uk.gov.hmcts.reform.sscs.service.placeholders.PlaceholderConstants;
import uk.gov.hmcts.reform.sscs.service.placeholders.SorPlaceholderService;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class SorWriteHandlerTest {
    @Mock
    private SorWriteHandler handler;

    @Mock
    private SorPlaceholderService sorPlaceholderService;

    @Mock
    private BulkPrintService bulkPrintService;

    @Mock
    private CoverLetterService coverLetterService;

    @Mock
    PdfStoreService pdfStoreService;

    @Captor
    ArgumentCaptor<String> argumentCaptor;

    @BeforeEach
    public void setup() {
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("name", "B-SCS-LET-ENG-Statement-Of-Reasons-Outcome.docx");
        nameMap.put("cover", "TB-SCS-GNO-ENG-00012.docx");
        Map<String, Map<String, String>> englishDocs = new HashMap<>();
        englishDocs.put(POST_HEARING_APP_SOR_WRITTEN.getCcdType(), nameMap);
        Map<LanguagePreference, Map<String, Map<String, String>>> template =  new HashMap<>();
        template.put(LanguagePreference.ENGLISH, englishDocs);

        DocmosisTemplateConfig docmosisTemplateConfig = new DocmosisTemplateConfig();
        docmosisTemplateConfig.setTemplate(template);

        handler = new SorWriteHandler(docmosisTemplateConfig, sorPlaceholderService, bulkPrintService,
            coverLetterService, pdfStoreService);
    }

    @Test
    void shouldReturnFalse_givenANonQualifyingCallbackType() {
        Callback<SscsCaseData> callback = buildTestCallbackForGivenData(SscsCaseData.builder().build(),
            READY_TO_LIST,
            NON_COMPLIANT);

        boolean result = handler.canHandle(ABOUT_TO_SUBMIT,  callback);
        Assertions.assertFalse(result);
    }

    @Test
    void shouldReturnFalse_givenANonQualifyingEvent() {
        Callback<SscsCaseData> callback = buildTestCallbackForGivenData(SscsCaseData.builder().build(),
            READY_TO_LIST,
            ISSUE_ADJOURNMENT_NOTICE);

        boolean result = handler.canHandle(SUBMITTED, callback);
        Assertions.assertFalse(result);
    }

    @Test
    void shouldThrowException_givenCallbackIsNull() {
        assertThrows(NullPointerException.class, () ->
            handler.canHandle(SUBMITTED, null)
        );
    }

    @Test
    void shouldThrowExceptionInHandler_givenNonValidCallback() {
        SscsCaseData caseData = buildCaseData();
        Callback<SscsCaseData> callback = buildTestCallbackForGivenData(caseData, READY_TO_LIST, ISSUE_ADJOURNMENT_NOTICE);

        assertThrows(IllegalStateException.class, () ->
            handler.handle(MID_EVENT, callback)
        );

        assertThrows(IllegalStateException.class, () ->
            handler.handle(SUBMITTED, callback)
        );
    }

    @Test
    void checkJointPartyAndAppointeeNameReturnsGivenCaseData() {
        SscsCaseData caseData = buildCaseData();
        caseData.setCcdCaseId("1");

        var sorDocumentDetails = SscsDocumentDetails.builder()
            .documentType(DocumentType.STATEMENT_OF_REASONS.getValue())
            .documentLink(DocumentLink.builder().documentUrl("url").build())
            .build();
        var sorDocument = SscsDocument.builder()
            .value(sorDocumentDetails)
            .build();
        caseData.setSscsDocument(List.of(sorDocument));
        caseData.getAppeal().getAppellant().setIsAppointee(YesNo.YES.getValue());
        caseData.getAppeal().getRep().setHasRepresentative(YesNo.NO.getValue());

        var jointParty = buildJointParty();
        jointParty.setHasJointParty(YesNo.YES);
        caseData.setJointParty(jointParty);

        Callback<SscsCaseData> callback = buildTestCallbackForGivenData(caseData, READY_TO_LIST, POST_HEARING_APP_SOR_WRITTEN);

        Map<String, Object> placeholders1 = new HashMap<>();
        placeholders1.put(PlaceholderConstants.NAME, caseData.getAppeal().getAppellant().getAppointee().getName().getFullNameNoTitle());
        when(sorPlaceholderService.populatePlaceholders(eq(caseData), eq(APPELLANT_LETTER), anyString(), eq(null))).thenReturn(placeholders1);

        Map<String, Object> placeholders2 = new HashMap<>();
        placeholders2.put(PlaceholderConstants.NAME, jointParty.getName().getFullNameNoTitle());
        when(sorPlaceholderService.populatePlaceholders(eq(caseData), eq(FurtherEvidenceLetterType.JOINT_PARTY_LETTER), anyString(), eq(null))).thenReturn(placeholders2);

        handler.handle(SUBMITTED, callback);

        verify(bulkPrintService, times(2)).sendToBulkPrint(eq(callback.getCaseDetails().getId()),
            eq(caseData), any(), eq(POST_HEARING_APP_SOR_WRITTEN),
            argumentCaptor.capture());
        Assertions.assertEquals(argumentCaptor.getAllValues(), List.of(
                caseData.getAppeal().getAppellant().getAppointee().getName().getFullNameNoTitle(),
                jointParty.getName().getFullNameNoTitle()));
    }

    @Test
    void checkAppointeeNameReturnsGivenSetName() {
        SscsCaseData caseData = buildCaseData();
        caseData.setCcdCaseId("1");

        var sorDocumentDetails = SscsDocumentDetails.builder()
            .documentType(DocumentType.STATEMENT_OF_REASONS.getValue())
            .documentLink(DocumentLink.builder().documentUrl("url").build())
            .build();
        var sorDocument = SscsDocument.builder()
            .value(sorDocumentDetails)
            .build();
        caseData.setSscsDocument(List.of(sorDocument));
        caseData.getAppeal().getAppellant().setIsAppointee(YesNo.YES.getValue());
        caseData.getAppeal().getRep().setHasRepresentative(YesNo.NO.getValue());

        var appointee = Appointee.builder()
            .name(Name.builder().firstName("OpFirstName").lastName("OpLastName").build())
            .build();

        Callback<SscsCaseData> callback = buildTestCallbackForGivenData(caseData, READY_TO_LIST, POST_HEARING_APP_SOR_WRITTEN);

        Map<String, Object> placeholders4 = new HashMap<>();
        placeholders4.put(PlaceholderConstants.NAME, appointee.getName().getFullNameNoTitle());
        when(sorPlaceholderService.populatePlaceholders(eq(caseData), eq(APPELLANT_LETTER), anyString(), eq(null))).thenReturn(placeholders4);

        handler.handle(SUBMITTED, callback);

        verify(bulkPrintService, times(1)).sendToBulkPrint(eq(callback.getCaseDetails().getId()),
            eq(caseData), any(), eq(POST_HEARING_APP_SOR_WRITTEN),
            argumentCaptor.capture());
        Assertions.assertEquals(argumentCaptor.getAllValues(), List.of(
            appointee.getName().getFullNameNoTitle()));
    }

    @Test
    void checkRepresentativeNameReturnsGivenCaseDataAndSetName() {
        SscsCaseData caseData = buildCaseData();
        caseData.setCcdCaseId("1");

        var sorDocumentDetails = SscsDocumentDetails.builder()
            .documentType(DocumentType.STATEMENT_OF_REASONS.getValue())
            .documentLink(DocumentLink.builder().documentUrl("url").build())
            .build();
        var sorDocument = SscsDocument.builder()
            .value(sorDocumentDetails)
            .build();
        caseData.setSscsDocument(List.of(sorDocument));

        Representative representative = Representative.builder()
            .hasRepresentative(YES)
            .name(Name.builder().firstName("OPRepFirstName").lastName("OPRepLastName").build())
            .build();

        Callback<SscsCaseData> callback = buildTestCallbackForGivenData(caseData, READY_TO_LIST, POST_HEARING_APP_SOR_WRITTEN);

        Map<String, Object> placeholders3 = new HashMap<>();
        placeholders3.put(PlaceholderConstants.NAME, caseData.getAppeal().getRep().getName().getFullNameNoTitle());
        when(sorPlaceholderService.populatePlaceholders(eq(caseData), eq(APPELLANT_LETTER), anyString(), eq(null))).thenReturn(placeholders3);

        Map<String, Object> placeholders5 = new HashMap<>();
        placeholders5.put(PlaceholderConstants.NAME, representative.getName().getFullNameNoTitle());
        when(sorPlaceholderService.populatePlaceholders(eq(caseData), eq(FurtherEvidenceLetterType.REPRESENTATIVE_LETTER), anyString(), eq(null))).thenReturn(placeholders5);

        handler.handle(SUBMITTED, callback);

        verify(bulkPrintService, times(2)).sendToBulkPrint(eq(callback.getCaseDetails().getId()),
            eq(caseData), any(), eq(POST_HEARING_APP_SOR_WRITTEN),
            argumentCaptor.capture());
        Assertions.assertEquals(argumentCaptor.getAllValues(), List.of(
            caseData.getAppeal().getRep().getName().getFullNameNoTitle(),
            representative.getName().getFullNameNoTitle()));
    }

    @Test
    void checkOtherPartyNameReturns() { //might not need these and above tests, only need to test if correct letter gets generated for each party as party name test is already created
        SscsCaseData caseData = buildCaseData();
        caseData.setCcdCaseId("1");

        var sorDocumentDetails = SscsDocumentDetails.builder()
            .documentType(DocumentType.STATEMENT_OF_REASONS.getValue())
            .documentLink(DocumentLink.builder().documentUrl("url").build())
            .build();
        var sorDocument = SscsDocument.builder()
            .value(sorDocumentDetails)
            .build();
        caseData.setSscsDocument(List.of(sorDocument));
//        caseData.getAppeal().getRep().setHasRepresentative(YesNo.NO.getValue());


        var otherParty = new CcdValue<>(buildOtherParty());

        var otherPartyWithRep = buildOtherParty();
        Representative representative = Representative.builder()
            .hasRepresentative(YES)
            .name(Name.builder().firstName("OPRepFirstName").lastName("OPRepLastName").build())
            .build();
        otherPartyWithRep.setRep(representative);

        caseData.setOtherParties(List.of(otherParty, new CcdValue<>(otherPartyWithRep)));

        Map<String, Object> placeholders1 = new HashMap<>();
        placeholders1.put(PlaceholderConstants.NAME, caseData.getAppeal().getAppellant().getName().getFullNameNoTitle());
        when(sorPlaceholderService.populatePlaceholders(eq(caseData), eq(APPELLANT_LETTER), anyString(), eq(null))).thenReturn(placeholders1);

        Map<String, Object> placeholders5 = new HashMap<>();
        placeholders5.put(PlaceholderConstants.NAME, otherPartyWithRep.getName().getFullNameNoTitle());
        when(sorPlaceholderService.populatePlaceholders(eq(caseData), eq(FurtherEvidenceLetterType.OTHER_PARTY_LETTER), anyString(), eq(null))).thenReturn(placeholders5);

        Callback<SscsCaseData> callback = buildTestCallbackForGivenData(caseData, READY_TO_LIST, POST_HEARING_APP_SOR_WRITTEN);

        handler.handle(SUBMITTED, callback);

        verify(bulkPrintService, times(5)).sendToBulkPrint(eq(callback.getCaseDetails().getId()),
            eq(caseData), any(), eq(POST_HEARING_APP_SOR_WRITTEN),
            argumentCaptor.capture());
        Assertions.assertEquals(List.of(
                caseData.getAppeal().getAppellant().getName().getFullNameNoTitle(),
                caseData.getAppeal().getRep().getName().getFullNameNoTitle(),
                caseData.getJointParty().getName().getFullNameNoTitle(),
                otherPartyWithRep.getName().getFullNameNoTitle()),
            argumentCaptor.getAllValues());
    }

    @Test
    void test1() { //test to sendLetter
        SscsCaseData caseData = buildCaseData();
        caseData.setCcdCaseId("1");

        var sorDocumentDetails = SscsDocumentDetails.builder()
            .documentType(DocumentType.STATEMENT_OF_REASONS.getValue())
            .documentLink(DocumentLink.builder().documentUrl("url").build())
            .build();
        var sorDocument = SscsDocument.builder()
            .value(sorDocumentDetails)
            .build();
        caseData.setSscsDocument(List.of(sorDocument));
        caseData.getAppeal().getAppellant().setIsAppointee(YesNo.YES.getValue());

        var jointParty = buildJointParty();
        jointParty.setHasJointParty(YesNo.YES);
        caseData.setJointParty(jointParty);

        var appointee = Appointee.builder()
            .name(Name.builder().firstName("OpFirstName").lastName("OpLastName").build())
            .build();

        var otherParty = buildOtherParty();
        Representative representative = Representative.builder()
            .hasRepresentative(YES)
            .name(Name.builder().firstName("OPRepFirstName").lastName("OPRepLastName").build())
            .build();
        caseData.setOtherParties(List.of(
            new CcdValue<>(otherParty)));
        Callback<SscsCaseData> callback = buildTestCallbackForGivenData(caseData, READY_TO_LIST, POST_HEARING_APP_SOR_WRITTEN);

        Map<String, Object> placeholders1 = new HashMap<>();
        placeholders1.put(PlaceholderConstants.NAME, caseData.getAppeal().getAppellant().getAppointee().getName().getFullNameNoTitle());
        when(sorPlaceholderService.populatePlaceholders(eq(caseData), eq(APPELLANT_LETTER), anyString(), eq(null))).thenReturn(placeholders1);

        handler.handle(SUBMITTED, callback);

        verify(coverLetterService).generateCoverLetterRetry(FurtherEvidenceLetterType.valueOf(argumentCaptor.capture()), any(), any(), any(), eq(1));
        Assertions.assertEquals(List.of(
                caseData.getAppeal().getAppellant().getAppointee().getName().getFullNameNoTitle(),
                jointParty.getName().getFullNameNoTitle(),
                caseData.getAppeal().getRep().getName().getFullNameNoTitle(),
                appointee.getName().getFullNameNoTitle(),
                otherParty.getName().getFullNameNoTitle(),
                representative.getName().getFullNameNoTitle()),
            argumentCaptor.getAllValues());
    }

    @Test
    void checkAppellantNameReturns() {
        SscsCaseData caseData = buildCaseData();
        caseData.setCcdCaseId("1");
        Callback<SscsCaseData> callback = buildTestCallbackForGivenData(caseData, READY_TO_LIST, POST_HEARING_APP_SOR_WRITTEN);

        var sorDocumentDetails = SscsDocumentDetails.builder()
            .documentType(DocumentType.STATEMENT_OF_REASONS.getValue())
            .documentLink(DocumentLink.builder().build())
            .build();
        var sorDocument = SscsDocument.builder()
            .value(sorDocumentDetails)
            .build();
        caseData.setSscsDocument(List.of(sorDocument));
        caseData.getAppeal().setRep(null);

        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put(PlaceholderConstants.NAME, caseData.getAppeal().getAppellant().getName().getFullNameNoTitle());
        when(sorPlaceholderService.populatePlaceholders(eq(caseData), eq(APPELLANT_LETTER), anyString(), eq(null))).thenReturn(placeholders);

        handler.handle(SUBMITTED, callback);

        verify(coverLetterService).generateCoverLetterRetry(eq(APPELLANT_LETTER), any(), any(), any(), eq(1));
        verify(bulkPrintService, times(1)).sendToBulkPrint(eq(callback.getCaseDetails().getId()),
            eq(caseData), any(), eq(POST_HEARING_APP_SOR_WRITTEN),
            eq(caseData.getAppeal().getAppellant().getName().getFullNameNoTitle()));
    }

}
