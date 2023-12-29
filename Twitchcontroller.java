package com.joycity.slg.twitch.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Controller
public class Twitchcontroller {
    //라이브, QA
    //private String baseUrl = "https://joycrew.joycity.com";
    //private String baseUrl = "https://qa-joycrew.joycity.com";
    //로컬 테스트 용
    private String baseUrl = "http://localhost:8080";
    private String TwitchRedirectUrlPath = "/join?v=2";
    private String clientID = "ymsdby563m8ozoofr5yuu5b0zgceag"; //설정해줘야함
    private String clientSecret = "1v5t1mtwhrl03fba8zn6uczhq4bz60"; //설정해줘야함

    private String stateSecret = "as124hAF143F34F14fdG6";

    @GetMapping("/{language}/twitch/login")
    public ResponseEntity<Object> instagramLogin(@PathVariable String language) {
        String twitchUrl = "https://id.twitch.tv/oauth2/authorize?"
                + "response_type=code"
                + "&client_id=" + clientID
                + "&redirect_uri=" + baseUrl + "/" + language + "/auth/twitch"
                + "&scope=channel%3Amanage%3Apolls+channel%3Aread%3Apolls"
                + "&state=" + stateSecret
                + "&nonce=" + stateSecret;

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setLocation(URI.create(twitchUrl));

        return new ResponseEntity<>(httpHeaders, HttpStatus.SEE_OTHER);
    }

    @GetMapping("/{language}/auth/twitch")
    public String authCallback(@RequestParam(name = "code", required = false) String code,
                               @RequestParam(name = "error", required = false) String error,
                               @PathVariable String language,
                               @RequestParam(name = "state", required = false) String state) {
        //에러가 리턴 됐을때
        if (error != null) { return baseUrl; }

        //스테이트가 다를때
        if (!state.equals(stateSecret)) { return baseUrl; }

        return "redirect:" + "http://localhost:8080" + "/" + language + TwitchRedirectUrlPath + "&code=" + code; //이거 나중에 바꿔줘야함
    }

    public void twitch_getTokens(@RequestParam(value = "code", required = false) String authCode, HttpServletResponse response, String language) {
        Map<String, String> str = reqTokens(authCode, response, language); //토큰들 들고오기 통과함
        Map<String, String> str2 = getUserInfo(str.get("access_token")); //유저 정보 들고오기
        int followers = getFollowersCount(str2.get("id"), str.get("access_token")); //팔로워 수 들고오기

        //여기에서 저장하는것만 만들어주면 될듯? 요?

    }

    //accessToken과 refreshToken을 받아와주는 함수
    public Map<String, String> reqTokens(String authCode, HttpServletResponse response, String language) {
        String twitchUrl = "https://id.twitch.tv/oauth2/token";

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        //파람에 Map<String, String> 형식으로 저장
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientID);
        params.add("client_secret", clientSecret);
        params.add("code", authCode);
        params.add("grant_type", "authorization_code");
        params.add("redirect_uri", baseUrl + "/" + language + "/auth/twitch");

        Map<String, String> str = new HashMap<>();
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, httpHeaders);
        RestTemplate restTemplate = new RestTemplateBuilder().build();

        try {
            ResponseEntity<String> res = restTemplate.postForEntity(twitchUrl, request, String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(res.getBody());

            str.put("access_token", jsonNode.get("access_token").asText());
            str.put("expires_in", jsonNode.get("expires_in").asText());
            str.put("refresh_token", jsonNode.get("refresh_token").asText());
            str.put("token_type", jsonNode.get("token_type").asText());
            return str;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return str;
    }

    //유저의 정보를 들고와주는 함수
    public Map<String, String> getUserInfo(String accessToken) {
        String twitchUrl = "https://api.twitch.tv/helix/users";

        Map<String, String> map = new HashMap<>();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Authorization", "Bearer " + accessToken);
        httpHeaders.set("Client-ID", clientID);

        HttpEntity<String> request = new HttpEntity<>(httpHeaders);
        RestTemplate restTemplate = new RestTemplateBuilder().build();

        try {
            ResponseEntity<String> res = restTemplate.exchange(twitchUrl, HttpMethod.GET, request, String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(res.getBody());
            JsonNode firstData = jsonNode.get("data").get(0);

            map.put("id", firstData.get("id").toString().replaceAll("\"", ""));
            map.put("login", firstData.get("login").toString());
            map.put("display_name", firstData.get("display_name").toString());
            map.put("created_at", firstData.get("created_at").toString());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return map;
    }

    //팔로워의 수를 리턴해주는 함수
    public int getFollowersCount(String userId, String accessToken) {

        int followersCount = 100;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Client-Id", clientID);

        HttpEntity<String> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplateBuilder().build();

        try {
            ResponseEntity<String> res = restTemplate.exchange("https://api.twitch.tv/helix/channels/followers?broadcaster_id=" + userId, HttpMethod.GET, request, String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(res.getBody());

            followersCount = jsonNode.get("total").asInt();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return followersCount;
    }

    //엑세스 토큰을 취소해주는 함수
    public void cancelAccessToken(String accessToken) {
        String twitchUrl = "https://id.twitch.tv/oauth2/revoke"
                + "?Content-Type: application/x-www-form-urlencoded"
                + "&client_id=" + clientID
                + "&token=" + accessToken;

        //필요한 것들 정의
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(headers);
        //정보 전송, 정보 받기
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        ResponseEntity<String> res = restTemplate.exchange(twitchUrl, HttpMethod.GET, request, String.class);

        System.out.println(res.getBody());
    }

    //refreshToken 으로 accessTokenr과 refreshToken을 다시 받아오는 함수
    public Map<String, String> refreshAccessToken(String refreshToken) {
        String requestUrl = "https://id.twitch.tv/oauth2/token";

        Map<String, String> map = new HashMap<>();

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        //파람에 Map<String, String> 형식으로 저장
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("Content-Type", "application/x-www-form-urlencoded");
        params.add("grant_type", "refresh_token");
        params.add("refresh_token", refreshToken);
        params.add("client_id", clientID);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, httpHeaders);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<String> res = restTemplate.postForEntity(requestUrl, request, String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(res.getBody());
            map.put("access_token", jsonNode.get("access_token").asText());
            map.put("refresh_token", jsonNode.get("refresh_token").asText());
        } catch (HttpClientErrorException e) {
            e.printStackTrace();
            String eCode = e.getStatusCode().toString();
            Cookie cookie = new Cookie("errorCode", eCode.split(" ")[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }
}
