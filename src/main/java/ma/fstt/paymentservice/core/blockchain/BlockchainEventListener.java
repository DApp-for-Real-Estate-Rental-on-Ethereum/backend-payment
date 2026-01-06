package ma.fstt.paymentservice.core.blockchain;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.fstt.paymentservice.core.orchestrator.PaymentOrchestrator;
import ma.fstt.paymentservice.domain.entity.BlockchainBlock;
import ma.fstt.paymentservice.domain.repository.BlockchainBlockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class BlockchainEventListener {

    private final PaymentOrchestrator paymentOrchestrator;
    private final BlockchainBlockRepository blockchainBlockRepository;

    @Value("${app.web3.rpc-url:http://blockchain-service:8545}")
    private String rpcUrl;

    @Value("${app.web3.contract-address}")
    private String contractAddress;

    private Web3j web3j;
    private BigInteger lastProcessedBlock;

    private static final Event BOOKING_PAYMENT_CREATED_EVENT = new Event("BookingPaymentCreated",
            Arrays.asList(
                    new TypeReference<Uint256>(true) {
                    }, // bookingId (indexed)
                    new TypeReference<Address>(true) {
                    }, // guest (indexed)
                    new TypeReference<Address>(true) {
                    }, // host (indexed)
                    new TypeReference<Uint256>(false) {
                    }, // rentAmount
                    new TypeReference<Uint256>(false) {
                    } // depositAmount
            ));

    @PostConstruct
    public void init() {
        try {
            web3j = Web3j.build(new HttpService(rpcUrl));

            String blockId = "PAYMENT_SERVICE";
            Optional<BlockchainBlock> savedBlock = blockchainBlockRepository.findById(blockId);

            if (savedBlock.isPresent()) {
                lastProcessedBlock = savedBlock.get().getLastProcessedBlock();
                log.info("BlockchainEventListener initialized. Resuming from persisted block: {}", lastProcessedBlock);
            } else {
                lastProcessedBlock = web3j.ethBlockNumber().send().getBlockNumber();
                log.info(
                        "BlockchainEventListener initialized. No persisted state found. Starting from current block: {}",
                        lastProcessedBlock);

                BlockchainBlock newBlock = BlockchainBlock.builder()
                        .id(blockId)
                        .lastProcessedBlock(lastProcessedBlock)
                        .build();
                blockchainBlockRepository.save(newBlock);
            }

        } catch (Exception e) {
            log.error("Failed to initialize BlockchainEventListener", e);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void pollForEvents() {
        if (web3j == null || contractAddress == null || contractAddress.isEmpty()) {
            return;
        }

        // Safety check if init failed
        if (lastProcessedBlock == null) {
            try {
                lastProcessedBlock = web3j.ethBlockNumber().send().getBlockNumber();
            } catch (Exception e) {
                log.error("Failed to recover lastProcessedBlock", e);
                return;
            }
        }

        try {
            BigInteger currentBlock = web3j.ethBlockNumber().send().getBlockNumber();
            if (currentBlock.compareTo(lastProcessedBlock) <= 0) {
                return;
            }

            EthFilter filter = new EthFilter(
                    DefaultBlockParameter.valueOf(lastProcessedBlock.add(BigInteger.ONE)),
                    DefaultBlockParameter.valueOf(currentBlock),
                    contractAddress);

            filter.addSingleTopic(EventEncoder.encode(BOOKING_PAYMENT_CREATED_EVENT));

            EthLog ethLog = web3j.ethGetLogs(filter).send();
            List<EthLog.LogResult> logs = ethLog.getLogs();

            for (EthLog.LogResult logResult : logs) {
                Log logData = (Log) logResult.get();
                processLog(logData);
            }

            lastProcessedBlock = currentBlock;

            // Persist state
            BlockchainBlock blockToUpdate = BlockchainBlock.builder()
                    .id("PAYMENT_SERVICE")
                    .lastProcessedBlock(lastProcessedBlock)
                    .build();
            blockchainBlockRepository.save(blockToUpdate);

        } catch (Exception e) {
            log.error("Error polling blockchain events", e);
        }
    }

    private void processLog(Log logData) {
        try {
            List<String> topics = logData.getTopics();
            if (topics == null || topics.isEmpty()) {
                return;
            }

            if (topics.size() < 2) {
                return;
            }

            String bookingIdHex = topics.get(1);
            BigInteger bookingIdBi = Numeric.toBigInt(bookingIdHex);
            Long bookingId = bookingIdBi.longValue();

            log.info("Detected BookingPaymentCreated event for Booking ID: {}", bookingId);

            try {
                paymentOrchestrator.completeBooking(bookingId);
                log.info("Successfully processed completion for Booking ID: {}", bookingId);
            } catch (Exception e) {
                log.error("Failed to complete booking " + bookingId, e);
            }

        } catch (Exception e) {
            log.error("Error processing log", e);
        }
    }
}
