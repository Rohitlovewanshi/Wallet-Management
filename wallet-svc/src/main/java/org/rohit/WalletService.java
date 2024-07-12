package org.rohit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class WalletService {

    private Logger logger = LoggerFactory.getLogger(WalletService.class);
    private JSONParser parser = new JSONParser();

    @Value("${wallet.promotional.balance}")
    private Long balance;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    // listener or watcher
    @KafkaListener(topics = {"user-created"}, groupId = "test123")
    public void createWallet(String msg){
        try {
            JSONObject obj = (JSONObject) this.parser.parse(msg);
            Long id = (Long) (obj.get("id"));
            Integer userId = id.intValue();

            Wallet wallet = this.walletRepository.findByUserId(userId);
            if (wallet != null){
                this.logger.info("Wallet for user - {} already created hence not creating again..");
                return;
            }

            wallet = Wallet.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .balance(balance)
                    .status(WalletStatus.ACTIVE)
                    .build();

            this.walletRepository.save(wallet);

        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @KafkaListener(topics = {"transaction-created"}, groupId = "test123")
    public void updateWallet(String msg){
        try {
            JSONObject obj = (JSONObject) this.parser.parse(msg);
            Long sender = (Long) (obj.get("sender"));
            Long receiver = (Long) (obj.get("receiver"));
            Long amount = (Long) (obj.get("amount"));
            String externalTxnId = (String) obj.get("externalTxnId");

            Integer senderId = sender.intValue();
            Integer receiverId = receiver.intValue();

            Wallet senderWallet = this.walletRepository.findByUserId(senderId);
            Wallet receiverWallet = this.walletRepository.findByUserId(receiverId);

            if (senderWallet == null || receiverWallet == null || amount <= 0 || senderWallet.getBalance() < amount){
                this.logger.error("Wallet update cannot be done due to either wallet not present or balance not sufficient");

                JSONObject event = new JSONObject();
                event.put("status", "FAILED");
                event.put("sender", senderId);
                event.put("receiver", receiverId);
                event.put("externalTxnId", externalTxnId);

                this.kafkaTemplate.send("wallet-updates", objectMapper.writeValueAsString(event));

                return;
            }

            //Approach 1
//            this.walletRepository.incrementWallet(receiverWallet.getId(), amount);
//            this.walletRepository.decrementWallet(senderWallet.getId(), amount);

            //Approach 2
//            this.walletRepository.updateWallet(receiverWallet.getId(), amount);
//            this.walletRepository.updateWallet(senderWallet.getId(), -amount);

            //Approach 3
            receiverWallet.setBalance(receiverWallet.getBalance() + amount);
            senderWallet.setBalance(senderWallet.getBalance() - amount);

            this.walletRepository.saveAll(List.of(receiverWallet, senderWallet));

            JSONObject event = new JSONObject();
            event.put("status", "SUCCESS");
            event.put("sender", senderId);
            event.put("receiver", receiverId);
            event.put("externalTxnId", externalTxnId);

            this.kafkaTemplate.send("wallet-updates", objectMapper.writeValueAsString(event));

        } catch (Exception e){
            e.printStackTrace();
        }
    }


}
