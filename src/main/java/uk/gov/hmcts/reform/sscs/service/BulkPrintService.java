package uk.gov.hmcts.reform.sscs.service;

import static java.lang.String.format;
import static java.util.Base64.getEncoder;
import static java.util.Collections.singletonList;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sendletter.api.LetterWithPdfsRequest;
import uk.gov.hmcts.reform.sendletter.api.SendLetterApi;
import uk.gov.hmcts.reform.sendletter.api.SendLetterResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.exception.BulkPrintException;
import uk.gov.hmcts.reform.sscs.idam.IdamService;

@Service
@Slf4j
public class BulkPrintService {

    private static final String XEROX_TYPE_PARAMETER = "DIV001";
    private static final String CCD_ID = "ccdId";
    private static final String LETTER_TYPE_KEY = "letterType";
    private static final String APPELLANT_NAME = "appellantName";

    private final SendLetterApi sendLetterApi;
    private final IdamService idamService;
    private final boolean sendLetterEnabled;

    @Autowired
    public BulkPrintService(SendLetterApi sendLetterApi, IdamService idamService,
                            @Value("${send-letter.enabled}") boolean sendLetterEnabled) {
        this.idamService = idamService;
        this.sendLetterApi = sendLetterApi;
        this.sendLetterEnabled = sendLetterEnabled;
    }

    public Optional<UUID> sendToBulkPrint(final String dataToPrint, final SscsCaseData sscsCaseData)
        throws BulkPrintException {
        if (sendLetterEnabled) {
            String encodedData = getEncoder().encodeToString(dataToPrint.getBytes());
            final String authToken = idamService.generateServiceAuthorization();
            return sendLetter(authToken, encodedData, sscsCaseData);
        }
        return Optional.empty();

    }

    private Optional<UUID> sendLetter(String authToken, String encodedData, SscsCaseData sscsCaseData) {
        try {
            SendLetterResponse sendLetterResponse = sendLetterApi.sendLetter(
                authToken,
                new LetterWithPdfsRequest(
                    singletonList(encodedData),
                    XEROX_TYPE_PARAMETER,
                    getAdditionalData(sscsCaseData)
                )
            );
            log.info("Letter service produced the following letter Id {} for case {}",
                sendLetterResponse.letterId, sscsCaseData.getCcdCaseId());

            return Optional.of(sendLetterResponse.letterId);
        } catch (Exception e) {
            String message = format("Failed to send to bulk print for case %s", sscsCaseData.getCcdCaseId());
            log.error(message, e);
            throw new BulkPrintException(message, e);
        }
    }

    private static Map<String, Object> getAdditionalData(final SscsCaseData sscsCaseData) {
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put(LETTER_TYPE_KEY, "sscs-data-pack");
        additionalData.put(CCD_ID, sscsCaseData.getCcdCaseId());
        additionalData.put(APPELLANT_NAME, sscsCaseData.getAppeal().getAppellant().getName().getFullNameNoTitle());
        return additionalData;
    }
}