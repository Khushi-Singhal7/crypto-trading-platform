package com.zosh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.ReadContext;
import com.zosh.model.CoinDTO;
import com.zosh.response.ApiResponse;
import com.zosh.response.FunctionResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class ChatBotServiceImpl implements ChatBotService {

    @Value("${gemini.api.key}")
    private String API_KEY;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Helper method for safe type conversion
    private double convertToDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    public CoinDTO makeApiRequest(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty() || currencyName.equals("N/A")) {
            return null;
        }

        String url = "https://api.coingecko.com/api/v3/coins/" + currencyName.toLowerCase();

        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> responseEntity = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> responseBody = responseEntity.getBody();

            if (responseBody != null) {
                CoinDTO coinInfo = new CoinDTO();
                coinInfo.setId((String) responseBody.get("id"));
                coinInfo.setSymbol((String) responseBody.get("symbol"));
                coinInfo.setName((String) responseBody.get("name"));

                Map<String, Object> image = (Map<String, Object>) responseBody.get("image");
                if (image != null) {
                    coinInfo.setImage((String) image.get("large"));
                }

                Map<String, Object> marketData = (Map<String, Object>) responseBody.get("market_data");
                if (marketData != null) {
                    Map<String, Object> currentPrice = (Map<String, Object>) marketData.get("current_price");
                    if (currentPrice != null) {
                        coinInfo.setCurrentPrice(convertToDouble(currentPrice.get("usd")));
                    }

                    Map<String, Object> marketCap = (Map<String, Object>) marketData.get("market_cap");
                    if (marketCap != null) {
                        coinInfo.setMarketCap(convertToDouble(marketCap.get("usd")));
                    }

                    coinInfo.setMarketCapRank(marketData.containsKey("market_cap_rank") ? (int) marketData.get("market_cap_rank") : 0);
                    coinInfo.setTotalVolume(convertToDouble(marketData.get("total_volume")));
                    coinInfo.setHigh24h(convertToDouble(marketData.get("high_24h")));
                    coinInfo.setLow24h(convertToDouble(marketData.get("low_24h")));
                    coinInfo.setPriceChange24h(convertToDouble(marketData.get("price_change_24h")));
                    coinInfo.setPriceChangePercentage24h(convertToDouble(marketData.get("price_change_percentage_24h")));
                    coinInfo.setMarketCapChange24h(convertToDouble(marketData.get("market_cap_change_24h")));
                    coinInfo.setMarketCapChangePercentage24h(convertToDouble(marketData.get("market_cap_change_percentage_24h")));
                    coinInfo.setCirculatingSupply(convertToDouble(marketData.get("circulating_supply")));
                    coinInfo.setTotalSupply(convertToDouble(marketData.get("total_supply")));
                }
                return coinInfo;
            }
        } catch (HttpClientErrorException.NotFound e) {
            System.err.println("Coin not found on CoinGecko: " + currencyName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public FunctionResponse getFunctionResponse(String prompt){
        String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        JSONObject functionDecl = new JSONObject();
        functionDecl.put("name", "getCoinDetails");
        functionDecl.put("description", "Get the coin details from given currency object");
        JSONObject parameters = new JSONObject();
        parameters.put("type", "OBJECT");
        JSONObject properties = new JSONObject();
        properties.put("currencyName", new JSONObject().put("type", "STRING").put("description", "The currency name, id, symbol."));
        properties.put("currencyData", new JSONObject().put("type", "STRING").put("description", "Currency Data id, symbol, name, image, current_price, market_cap, market_cap_rank, fully_diluted_valuation, total_volume, high_24h, low_24h, price_change_24h, price_change_percentage_24h, market_cap_change_24h, market_cap_change_percentage_24h, circulating_supply, total_supply, max_supply, ath, ath_change_percentage, ath_date, atl, atl_change_percentage, atl_date, last_updated."));
        parameters.put("properties", properties);
        parameters.put("required", new JSONArray(new String[]{"currencyName", "currencyData"}));
        functionDecl.put("parameters", parameters);

        JSONObject tool = new JSONObject();
        tool.put("functionDeclarations", new JSONArray().put(functionDecl));

        JSONObject requestBody = new JSONObject();
        requestBody.put("contents", new JSONArray()
                .put(new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", prompt))))
        );
        requestBody.put("tools", new JSONArray().put(tool));

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody.toString(), headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_API_URL, requestEntity, String.class);

        String responseBody = response.getBody();
        ReadContext ctx = JsonPath.parse(responseBody);
        FunctionResponse res = new FunctionResponse();

        try {
            String name = ctx.read("$.candidates[0].content.parts[0].functionCall.name");
            String currencyName = ctx.read("$.candidates[0].content.parts[0].functionCall.args.currencyName");
            String currencyData = ctx.read("$.candidates[0].content.parts[0].functionCall.args.currencyData");

            res.setFunctionName(name != null ? name : "N/A");
            res.setCurrencyName(currencyName != null ? currencyName.toLowerCase() : "N/A");
            res.setCurrencyData(currencyData != null ? currencyData : "N/A");

            System.out.println(res.getFunctionName() + " ------- " + res.getCurrencyName() + "-----" + res.getCurrencyData());
        } catch (PathNotFoundException e) {
            System.out.println("No function call detected. Attempting to read text response.");
            try {
                String textResponse = ctx.read("$.candidates[0].content.parts[0].text");
                res.setCurrencyData(textResponse != null ? textResponse : "No response available.");
                System.out.println("Text response: " + res.getCurrencyData());
            } catch (PathNotFoundException textE) {
                System.err.println("Error: The response does not contain a function call or a text part.");
                res.setCurrencyData("Error: No valid response found.");
            }
        }
        return res;
    }

    @Override
    public ApiResponse getCoinDetails(String prompt) {
        FunctionResponse res = getFunctionResponse(prompt);

        if (res.getFunctionName() == null || !res.getFunctionName().equals("getCoinDetails") || "N/A".equals(res.getCurrencyName())) {
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setMessage(res.getCurrencyData());
            return apiResponse;
        }

        CoinDTO coinData = makeApiRequest(res.getCurrencyName());

        JSONObject apiResponseJson;
        if (coinData != null) {
            apiResponseJson = new JSONObject(objectMapper.convertValue(coinData, Map.class));
        } else {
            apiResponseJson = new JSONObject().put("error", "No data found for the currency.");
        }

        String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        JSONObject requestBody = new JSONObject();
        requestBody.put("contents", new JSONArray()
                .put(new JSONObject()
                        .put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", prompt))))
                .put(new JSONObject()
                        .put("role", "function")
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("functionResponse", new JSONObject()
                                        .put("name", "getCoinDetails")
                                        .put("response", new JSONObject()
                                                .put("content", apiResponseJson)))))));

        HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_API_URL, request, String.class);

        System.out.println("Response: " + response.getBody());
        ReadContext ctx = JsonPath.parse(response.getBody());

        String text = null;
        try {
            text = ctx.read("$.candidates[0].content.parts[0].text");
        } catch (PathNotFoundException e) {
            System.err.println("Text not found in the final response from the model.");
        }

        ApiResponse ans = new ApiResponse();
        ans.setMessage(text != null ? text : "Could not generate a text response from the model.");
        return ans;
    }

    @Override
    public CoinDTO getCoinByName(String coinName) {
        return this.makeApiRequest(coinName);
    }

    @Override
    public String simpleChat(String prompt) {
        String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        JSONObject requestBody = new JSONObject();
        requestBody.put("contents", new JSONArray()
                .put(new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", prompt))))
        );

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody.toString(), headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_API_URL, requestEntity, String.class);

        String responseBody = response.getBody();
        System.out.println("Response Body: " + responseBody);

        try {
            ReadContext ctx = JsonPath.parse(responseBody);
            String textResponse = ctx.read("$.candidates[0].content.parts[0].text");
            return textResponse;
        } catch (PathNotFoundException e) {
            return "Could not parse response: " + responseBody;
        }
    }
}
