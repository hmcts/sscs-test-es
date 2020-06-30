package uk.gov.hmcts.reform.sscs.functional;

import static org.junit.Assert.assertEquals;
import static uk.gov.hmcts.reform.sscs.ccd.domain.EventType.VALID_APPEAL_CREATED;

import io.github.artsok.RepeatedIfExceptionsTest;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

public class EvidenceShareFunctionalTest extends AbstractFunctionalTest {

    public EvidenceShareFunctionalTest() {
        super();
    }

    @BeforeEach
    public void beforeEach() {
        ccdCaseId = null;
    }

    @RepeatedIfExceptionsTest(repeats = 3)
    public void processAnAppealWithValidMrn_shouldGenerateADl6AndAddToCcdAndUpdateState() throws Exception {
        Thread.sleep(5000);

        System.out.println("Test 1 Case Id pre " + ccdCaseId);

        IdamTokens idamToks = idamService.getIdamTokens();
        System.out.println("idam token  is " + idamToks.getIdamOauth2Token());

        createCaseWithValidAppealState(VALID_APPEAL_CREATED);

        System.out.println("Test 1 Case Id post" + ccdCaseId);

        String json = getJson(VALID_APPEAL_CREATED.getCcdType());
        json = json.replace("CASE_ID_TO_BE_REPLACED", ccdCaseId);
        json = json.replace("MRN_DATE_TO_BE_REPLACED", LocalDate.now().toString());

        simulateCcdCallback(json);

        SscsCaseDetails caseDetails = findCaseById(ccdCaseId);

        System.out.println("Test 1 Found case" + caseDetails.toString());

        SscsCaseData caseData = caseDetails.getData();

        List<SscsDocument> docs = caseData.getSscsDocument();
        assertEquals(1, docs.size());
        assertEquals("dl6-" + ccdCaseId + ".pdf", docs.get(0).getValue().getDocumentFileName());
        assertEquals("withDwp", caseDetails.getState());
        assertEquals(LocalDate.now().toString(), caseData.getDateSentToDwp());
        assertEquals(LocalDate.now().toString(), caseData.getDateCaseSentToGaps());
    }
}
