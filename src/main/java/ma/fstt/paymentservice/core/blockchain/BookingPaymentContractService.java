package ma.fstt.paymentservice.core.blockchain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class BookingPaymentContractService {

    @Value("${app.web3.rpc-url:http://127.0.0.1:8545}")
    private String rpcUrl;

    @Value("${app.web3.contract-address:}")
    private String contractAddress;

    @Value("${app.web3.private-key:}")
    private String privateKey;

    private Web3j web3j;

    private Web3j getWeb3j() {
        if (web3j == null) {
            web3j = Web3j.build(new HttpService(rpcUrl));
        }
        return web3j;
    }

    private Transaction createValidatedCallTransaction(String fromAddress, String toAddress, String data) {
        final String DEFAULT_ADMIN_ADDRESS = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
        
        String validatedFromAddress = fromAddress;
        if (validatedFromAddress == null || 
            validatedFromAddress.trim().isEmpty() ||
            validatedFromAddress.equals("0x0000000000000000000000000000000000000000") ||
            validatedFromAddress.equals("0x0") ||
            validatedFromAddress.equalsIgnoreCase("null")) {
            validatedFromAddress = DEFAULT_ADMIN_ADDRESS;
        } else {
            validatedFromAddress = validatedFromAddress.trim();
            if (!validatedFromAddress.startsWith("0x")) {
                validatedFromAddress = "0x" + validatedFromAddress;
            }
            validatedFromAddress = validatedFromAddress.toLowerCase();
        }
        
        return new Transaction(
                validatedFromAddress,
                null,
                null,
                null,
                toAddress,
                null,
                data
        );
    }

    public String getCreateBookingPaymentData(
            Long bookingId,
            String hostWalletAddress,
            String tenantWalletAddress,
            BigInteger rentAmountWei,
            BigInteger depositAmountWei
    ) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        Function function = new Function(
                "createBookingPayment",
                Arrays.asList(
                        new Uint256(BigInteger.valueOf(bookingId)),
                        new org.web3j.abi.datatypes.Address(hostWalletAddress),
                        new org.web3j.abi.datatypes.Address(tenantWalletAddress),
                        new Uint256(rentAmountWei),
                        new Uint256(depositAmountWei)
                ),
                Collections.emptyList()
        );

        return FunctionEncoder.encode(function);
    }

    public String createBookingPayment(
            Long bookingId,
            String guestWalletAddress,
            String hostWalletAddress,
            BigInteger rentAmountWei,
            BigInteger depositAmountWei,
            String guestPrivateKey
    ) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        String encodedFunction = getCreateBookingPaymentData(bookingId, hostWalletAddress, guestWalletAddress, rentAmountWei, depositAmountWei);
        BigInteger totalAmount = rentAmountWei.add(depositAmountWei);

        if (guestPrivateKey == null || guestPrivateKey.isEmpty()) {
            throw new IllegalStateException("Guest private key is required for sending transactions");
        }

        Credentials credentials = Credentials.create(guestPrivateKey);
        long chainId = getChainId().longValue();
        RawTransactionManager transactionManager = new RawTransactionManager(
                getWeb3j(),
                credentials,
                chainId
        );

        DefaultGasProvider gasProvider = new DefaultGasProvider();
        EthSendTransaction response = transactionManager.sendTransaction(
                gasProvider.getGasPrice(),
                gasProvider.getGasLimit(),
                contractAddress,
                encodedFunction,
                totalAmount
        );

        if (response.hasError()) {
            throw new RuntimeException("Transaction failed: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        return txHash;
    }

    public String completeBooking(Long bookingId) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        try {
            long chainId = getChainId().longValue();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot connect to blockchain network: " + e.getMessage());
        }
        
        try {
            org.web3j.protocol.core.methods.response.EthGetCode ethGetCode = getWeb3j().ethGetCode(contractAddress, DefaultBlockParameterName.LATEST).send();
            String contractCode = ethGetCode.getCode();
            if (contractCode == null || contractCode.isEmpty() || contractCode.equals("0x")) {
                throw new IllegalStateException("Contract not found at address: " + contractAddress + ". Please deploy the contract first.");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot verify contract at address " + contractAddress + ": " + e.getMessage());
        }

        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalStateException("Private key not configured for sending transactions");
        }
        
        String normalizedPrivateKey = privateKey.trim();
        if (normalizedPrivateKey.startsWith("0x")) {
            normalizedPrivateKey = normalizedPrivateKey.substring(2);
        }
        normalizedPrivateKey = "0x" + normalizedPrivateKey;
        
        Credentials credentials;
        try {
            credentials = Credentials.create(normalizedPrivateKey);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid private key format: " + e.getMessage());
        }
        
        String walletAddress = credentials.getAddress();

        String adminAddress = null;
        String hostAddressFromBooking = null;
        BigInteger rentAmount = BigInteger.ZERO;
        BigInteger depositAmount = BigInteger.ZERO;
        
        try {
            Function adminFunction = new Function(
                    "admin",
                    Collections.emptyList(),
                    Arrays.asList(new TypeReference<Address>() {})
            );
            String encodedAdminFunction = FunctionEncoder.encode(adminFunction);
            
            Transaction adminTransaction = createValidatedCallTransaction(walletAddress, contractAddress, encodedAdminFunction);
            
            EthCall adminResponse = getWeb3j().ethCall(adminTransaction, DefaultBlockParameterName.LATEST).send();
            
            if (!adminResponse.hasError() && adminResponse.getValue() != null && !adminResponse.getValue().isEmpty()) {
                List<Type> adminDecoded = FunctionReturnDecoder.decode(adminResponse.getValue(), adminFunction.getOutputParameters());
                if (!adminDecoded.isEmpty()) {
                    adminAddress = ((Address) adminDecoded.get(0)).getValue();
                }
            }
        } catch (Exception e) {
        }
        
        try {
            List<Object> bookingDetails = getBooking(bookingId, walletAddress);
            if (bookingDetails != null && bookingDetails.size() >= 4) {
                String guestAddress = (String) bookingDetails.get(0);
                hostAddressFromBooking = (String) bookingDetails.get(1);
                rentAmount = (BigInteger) bookingDetails.get(2);
                depositAmount = (BigInteger) bookingDetails.get(3);
                
                if (rentAmount.equals(BigInteger.ZERO)) {
                    throw new IllegalStateException("Booking already completed on blockchain");
                }
            }
        } catch (Exception e) {
        }

        Function function = new Function(
                "completeBooking",
                Arrays.asList(new Uint256(BigInteger.valueOf(bookingId))),
                Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(function);
        
        String hardhatAdminAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
        
        boolean isAuthorized = false;
        if (adminAddress != null && walletAddress.equalsIgnoreCase(adminAddress)) {
            isAuthorized = true;
        } else if (walletAddress.equalsIgnoreCase(hardhatAdminAddress)) {
            isAuthorized = true;
        } else if (hostAddressFromBooking != null && walletAddress.equalsIgnoreCase(hostAddressFromBooking)) {
            isAuthorized = true;
        } else {
            throw new IllegalStateException(
                String.format("Authorization failed: Private key wallet (%s) is neither admin (%s) nor host (%s). " +
                    "Please update app.web3.private-key in application.properties to match the contract admin's private key.", 
                    walletAddress, 
                    adminAddress != null ? adminAddress : "unknown",
                    hostAddressFromBooking != null ? hostAddressFromBooking : "unknown")
            );
        }
        
        long chainId = getChainId().longValue();
        
        RawTransactionManager transactionManager = new RawTransactionManager(
                getWeb3j(),
                credentials,
                chainId
        );

        DefaultGasProvider gasProvider = new DefaultGasProvider();
        BigInteger gasPrice = gasProvider.getGasPrice();
        BigInteger gasLimit = gasProvider.getGasLimit();

        try {
            Function balanceFunction = new Function(
                    "getContractBalance",
                    Collections.emptyList(),
                    Arrays.asList(new TypeReference<Uint256>() {})
            );
            String encodedBalanceFunction = FunctionEncoder.encode(balanceFunction);
            Transaction balanceTransaction = createValidatedCallTransaction(walletAddress, contractAddress, encodedBalanceFunction);
            EthCall balanceResponse = getWeb3j().ethCall(balanceTransaction, DefaultBlockParameterName.LATEST).send();
            if (!balanceResponse.hasError() && balanceResponse.getValue() != null) {
                List<Type> balanceDecoded = FunctionReturnDecoder.decode(balanceResponse.getValue(), balanceFunction.getOutputParameters());
                if (!balanceDecoded.isEmpty()) {
                    BigInteger contractBalance = (BigInteger) balanceDecoded.get(0).getValue();
                    BigInteger expectedBalance = rentAmount.add(depositAmount);
                    
                    if (contractBalance.compareTo(expectedBalance) < 0) {
                        BigInteger missing = expectedBalance.subtract(contractBalance);
                        throw new IllegalStateException("Contract balance is insufficient. Expected: " + expectedBalance + " Wei, Actual: " + contractBalance + " Wei. Missing: " + missing + " Wei. The payment may not have been sent to the contract.");
                    }
                }
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
        }
        
        EthSendTransaction response = transactionManager.sendTransaction(
                gasPrice,
                gasLimit,
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
        );

        if (response.hasError()) {
            throw new RuntimeException("Transaction failed: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        
        try {
            int maxAttempts = 30;
            int attempt = 0;
            TransactionReceipt receipt = null;
            
            while (attempt < maxAttempts) {
                try {
                    EthGetTransactionReceipt ethGetTransactionReceipt = 
                            getWeb3j().ethGetTransactionReceipt(txHash).send();
                    
                    if (ethGetTransactionReceipt.getTransactionReceipt().isPresent()) {
                        receipt = ethGetTransactionReceipt.getTransactionReceipt().get();
                        break;
                    }
                } catch (Exception e) {
                }
                
                Thread.sleep(1000);
                attempt++;
            }
            
            if (receipt != null) {
                if (receipt.getStatus().equals("0x0")) {
                    throw new RuntimeException("Transaction reverted on blockchain.");
                } else {
                    try {
                        Thread.sleep(3000);
                        List<Object> bookingDetailsAfter = getBooking(bookingId, walletAddress);
                        if (bookingDetailsAfter != null && bookingDetailsAfter.size() >= 4) {
                            BigInteger rentAmountAfter = (BigInteger) bookingDetailsAfter.get(2);
                            BigInteger depositAmountAfter = (BigInteger) bookingDetailsAfter.get(3);
                            
                            if (!rentAmountAfter.equals(BigInteger.ZERO) || !depositAmountAfter.equals(BigInteger.ZERO)) {
                                throw new RuntimeException("Funds were not distributed. Booking still has funds: rentAmount=" + rentAmountAfter + " Wei, depositAmount=" + depositAmountAfter + " Wei");
                            }
                        }
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                    }
                }
            } else {
                throw new RuntimeException("Transaction sent but not confirmed. Receipt not found after " + maxAttempts + " attempts. Transaction hash: " + txHash);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Transaction confirmation interrupted: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to confirm transaction receipt: " + e.getMessage(), e);
        }
        
        return txHash;
    }

    public boolean bookingExists(Long bookingId) throws Exception {
        return bookingExists(bookingId, null);
    }
    
    public boolean bookingExists(Long bookingId, String fromAddress) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            return false;
        }

        Function function = new Function(
                "bookingExistsCheck",
                Arrays.asList(new Uint256(BigInteger.valueOf(bookingId))),
                Arrays.asList(new TypeReference<org.web3j.abi.datatypes.Bool>() {})
        );

        String encodedFunction = FunctionEncoder.encode(function);
        Transaction transaction = createValidatedCallTransaction(fromAddress, contractAddress, encodedFunction);
        EthCall response = getWeb3j().ethCall(transaction, DefaultBlockParameterName.LATEST).send();

        if (response.hasError()) {
            return false;
        }

        String value = response.getValue();
        List<Type> decoded = FunctionReturnDecoder.decode(value, function.getOutputParameters());
        if (decoded.isEmpty()) {
            return false;
        }
        return (Boolean) decoded.get(0).getValue();
    }

    public List<Object> getBooking(Long bookingId) throws Exception {
        return getBooking(bookingId, null);
    }
    
    public List<Object> getBooking(Long bookingId, String fromAddress) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        Function function = new Function(
                "getBooking",
                Arrays.asList(new Uint256(BigInteger.valueOf(bookingId))),
                Arrays.asList(
                        new TypeReference<org.web3j.abi.datatypes.Address>() {},
                        new TypeReference<org.web3j.abi.datatypes.Address>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {}
                )
        );

        String encodedFunction = FunctionEncoder.encode(function);
        
        Transaction transaction = createValidatedCallTransaction(fromAddress, contractAddress, encodedFunction);
        
        EthCall response = getWeb3j().ethCall(transaction, DefaultBlockParameterName.LATEST).send();

        if (response.hasError()) {
            throw new RuntimeException("Contract call failed: " + response.getError().getMessage());
        }

        String value = response.getValue();
        if (value == null || value.isEmpty() || value.equals("0x")) {
            throw new RuntimeException("Booking not found on blockchain: " + bookingId);
        }

        List<Type> decoded = FunctionReturnDecoder.decode(value, function.getOutputParameters());

        if (decoded == null || decoded.isEmpty()) {
            throw new RuntimeException("Failed to decode booking data");
        }

        return decoded.stream().map(Type::getValue).toList();
    }

    private BigInteger getChainId() throws Exception {
        return getWeb3j().ethChainId().send().getChainId();
    }

    public String processReclamationRefund(
            Long bookingId,
            String recipientAddress,
            BigInteger refundAmountWei,
            BigInteger penaltyAmountWei,
            boolean refundFromRent
    ) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalStateException("Private key not configured");
        }

        Credentials credentials = Credentials.create(privateKey);
        String walletAddress = credentials.getAddress();

        Function function = new Function(
                "processReclamationRefund",
                Arrays.asList(
                        new Uint256(BigInteger.valueOf(bookingId)),
                        new Address(recipientAddress),
                        new Uint256(refundAmountWei),
                        new Uint256(penaltyAmountWei),
                        new org.web3j.abi.datatypes.Bool(refundFromRent)
                ),
                Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(function);
        long chainId = getChainId().longValue();

        RawTransactionManager transactionManager = new RawTransactionManager(
                getWeb3j(),
                credentials,
                chainId
        );

        DefaultGasProvider gasProvider = new DefaultGasProvider();
        EthSendTransaction response = transactionManager.sendTransaction(
                gasProvider.getGasPrice(),
                gasProvider.getGasLimit(),
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
        );

        if (response.hasError()) {
            throw new RuntimeException("Transaction failed: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        return txHash;
    }

    public String processPartialRefund(
            Long bookingId,
            String recipientAddress,
            BigInteger refundAmountWei,
            boolean refundFromRent
    ) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalStateException("Private key not configured");
        }

        Credentials credentials = Credentials.create(privateKey);
        Function function = new Function(
                "processPartialRefund",
                Arrays.asList(
                        new Uint256(BigInteger.valueOf(bookingId)),
                        new Address(recipientAddress),
                        new Uint256(refundAmountWei),
                        new org.web3j.abi.datatypes.Bool(refundFromRent)
                ),
                Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(function);
        long chainId = getChainId().longValue();

        RawTransactionManager transactionManager = new RawTransactionManager(
                getWeb3j(),
                credentials,
                chainId
        );

        DefaultGasProvider gasProvider = new DefaultGasProvider();
        EthSendTransaction response = transactionManager.sendTransaction(
                gasProvider.getGasPrice(),
                gasProvider.getGasLimit(),
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
        );

        if (response.hasError()) {
            throw new RuntimeException("Transaction failed: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        return txHash;
    }

    public String setActiveReclamation(Long bookingId, boolean active) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalStateException("Private key not configured");
        }

        Credentials credentials = Credentials.create(privateKey);
        Function function = new Function(
                "setActiveReclamation",
                Arrays.asList(
                        new Uint256(BigInteger.valueOf(bookingId)),
                        new org.web3j.abi.datatypes.Bool(active)
                ),
                Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(function);
        long chainId = getChainId().longValue();

        RawTransactionManager transactionManager = new RawTransactionManager(
                getWeb3j(),
                credentials,
                chainId
        );

        DefaultGasProvider gasProvider = new DefaultGasProvider();
        EthSendTransaction response = transactionManager.sendTransaction(
                gasProvider.getGasPrice(),
                gasProvider.getGasLimit(),
                contractAddress,
                encodedFunction,
                BigInteger.ZERO
        );

        if (response.hasError()) {
            throw new RuntimeException("Transaction failed: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        return txHash;
    }

    public List<Object> getReclamationRefund(Long bookingId) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        Function function = new Function(
                "getReclamationRefund",
                Arrays.asList(new Uint256(BigInteger.valueOf(bookingId))),
                Arrays.asList(
                        new TypeReference<org.web3j.abi.datatypes.Address>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<org.web3j.abi.datatypes.Bool>() {}
                )
        );

        String encodedFunction = FunctionEncoder.encode(function);
        Transaction transaction = createValidatedCallTransaction(null, contractAddress, encodedFunction);
        EthCall response = getWeb3j().ethCall(transaction, DefaultBlockParameterName.LATEST).send();

        if (response.hasError()) {
            throw new RuntimeException("Contract call failed: " + response.getError().getMessage());
        }

        String value = response.getValue();
        List<Type> decoded = FunctionReturnDecoder.decode(value, function.getOutputParameters());
        return decoded.stream().map(Type::getValue).toList();
    }

    public List<Object> getBookingWithReclamation(Long bookingId) throws Exception {
        return getBookingWithReclamation(bookingId, null);
    }

    public List<Object> getBookingWithReclamation(Long bookingId, String fromAddress) throws Exception {
        if (contractAddress == null || contractAddress.isEmpty()) {
            throw new IllegalStateException("Contract address not configured");
        }

        Function function = new Function(
                "getBookingWithReclamation",
                Arrays.asList(new Uint256(BigInteger.valueOf(bookingId))),
                Arrays.asList(
                        new TypeReference<org.web3j.abi.datatypes.Address>() {},
                        new TypeReference<org.web3j.abi.datatypes.Address>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<org.web3j.abi.datatypes.Bool>() {},
                        new TypeReference<org.web3j.abi.datatypes.Bool>() {}
                )
        );

        String encodedFunction = FunctionEncoder.encode(function);
        
        Transaction transaction = createValidatedCallTransaction(fromAddress, contractAddress, encodedFunction);
        
        EthCall response = getWeb3j().ethCall(transaction, DefaultBlockParameterName.LATEST).send();

        if (response.hasError()) {
            throw new RuntimeException("Contract call failed: " + response.getError().getMessage());
        }

        String value = response.getValue();
        if (value == null || value.isEmpty() || value.equals("0x")) {
            throw new RuntimeException("Booking not found on blockchain: " + bookingId);
        }

        List<Type> decoded = FunctionReturnDecoder.decode(value, function.getOutputParameters());

        if (decoded == null || decoded.isEmpty()) {
            throw new RuntimeException("Failed to decode booking data");
        }

        return decoded.stream().map(Type::getValue).toList();
    }
}
