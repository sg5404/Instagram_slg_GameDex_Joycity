package com.joycity.slg.instagram.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joycity.slg.token.JwtTokenizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;

@Controller
public class InstagramController {
    //라이브, QA
    //private String baseUrl = "https://joycrew.joycity.com";
    private String baseUrl = "https://qa-joycrew.joycity.com";
    //로컬 테스트 용
    //private String baseUrl = "http://localhost:8080";
    private String InstagramRedirectUrlPath = "/join?v=2";
    //rivate String clientID = "1896667810730457";
    //private String clientSecret = "92e5a0fa38e68c77607c4aeab0ff41bb";

    private  String clientID = "1896667810730457";
    private String clientSecret = "92e5a0fa38e68c77607c4aeab0ff41bb";

    private final JwtTokenizer jwtTokenizer = new JwtTokenizer(clientSecret);

    /**
     * client_id와 값을 리턴해줄 링크를 보내주면, 로그인 완료 시, 해당링크로 보내줌
     * @요청값: clientId, redirectUri, scope, responseType, state(어디서, 왜 쓰는지 모르겠음)
     * @리턴값: redirect_uri + code
     */
    @GetMapping("/{language}/instagram/login")
    public ResponseEntity<Object> instagramLogin(@PathVariable String language) {
        String instagramUrl = "https://api.instagram.com/oauth/authorize?"
                + "client_id=" + clientID
                + "&redirect_uri=" + baseUrl + "/" + language + "/auth/instagram"
                + "&scope=" + "user_profile,user_media"
                + "&response_type=code";

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setLocation(URI.create(instagramUrl));

        return new ResponseEntity<>(httpHeaders, HttpStatus.SEE_OTHER);
    }

    /**
     * 용도: 위의 함수에서 이어지는 함수
     */
    @GetMapping("/{language}/auth/instagram") //이거 링크 나중에 바꿔줘야함
    public String authCallback(@RequestParam("code") String code, @PathVariable String language) {

        System.out.println("Received code: " + code);
        return "redirect:" + "http://localhost:8080" + "/" + language + InstagramRedirectUrlPath + "&code=" + code;
    }

    //쿠키를 저장해주는 함수
    public void instagram_getAccessToken(@RequestParam(value = "code", required = false) String authCode, HttpServletResponse response, String language)
    {
        System.out.println("실행됨");
        InstagramUserInfoDTO UII = reqInstagramAccessToken(authCode, response, language);
        String userName = UII.getUserName();
        //아래 함수는 jwtTokenizer 예시 코드
        //String refresh_token =  jwtTokenizer.createToken(UII.getUserId(), UII.getUserName()); //이런식으로 토큰 생성
        //System.out.println("refresh_token : " + refresh_token);
        Cookie cookie = new Cookie("IUN", userName);

        cookie.setPath("/en");
        cookie.setMaxAge(60*60*24);

        response.addCookie(cookie);
    }

    /**
     * 요청값을 받아와서, access_token을 발급해주는 함수
     * @요청값: client_id, client_secret, code, grant_type, redirect_uri
     * @리턴값: InstagramUserInfo(유저 아이디, 유저 이름, 유저 프로필 링크, accessToken)
     * @관련문서: https://developers.facebook.com/docs/instagram-basic-display-api/reference/oauth-access-token
     */
    public InstagramUserInfoDTO reqInstagramAccessToken(String authCode, HttpServletResponse response, String language) {
        //정보를 통신할 링크
        String instagramUrl = "https://api.instagram.com/oauth/access_token";

        //필요한 것들 정의
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        //파람에 Map<String, String> 형식으로 저장
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientID);
        params.add("client_secret", clientSecret);
        params.add("code", authCode);
        params.add("grant_type", "authorization_code");
        params.add("redirect_uri", baseUrl + "/" + language + "/auth/instagram");

        //정보 통신
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, httpHeaders);
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();

        System.out.println("실행됨");

        try {
            ResponseEntity<String> res = restTemplate.postForEntity(instagramUrl, request, String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(res.getBody());

            String accessTokenValue = jsonNode.get("access_token").asText();
            //longAccessToken(accessTokenValue);
            return getUserInfo(accessTokenValue);

        } catch (HttpClientErrorException e) {
            e.printStackTrace();
            String eCode = e.getStatusCode().toString();
            System.out.println("ErrorCode : " + eCode);
            System.out.println("Error during Request Instagram AccessToken at HttpClient : " + e.getMessage());

            return new InstagramUserInfoDTO("", "", "", "");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error during Request Instagram AccessToken : " + e.getMessage());

            return new InstagramUserInfoDTO("", "", "", "");
        }
    }

    /**
     *
     * @요청값: accessToken, userIdValue
     * @리턴값: InstagramUserInfo(유저 아이디, 유저 이름, 유저 프로필 링크, accessToken)
     * @관련문서: https://developers.facebook.com/docs/instagram-basic-display-api/reference/user#---
     */
    public InstagramUserInfoDTO getUserInfo(String accessToken) {
        //자신의 정보를 들고 오는 코드, fields 값을 조정하여 다른 정보들을 추가로 들고올 수 있다.
        String requestUrl = "https://graph.instagram.com/v18.0/me"
                + "?fields=id,username"
                + "&access_token=" + accessToken;

        String userInstagramName, userInstagramId = "";

        //필요한 것들 정의
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(headers);

        //정보 전송, 정보 받기
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();

        ResponseEntity<String> res = restTemplate.exchange(requestUrl, HttpMethod.GET, request, String.class);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(res.getBody());

            userInstagramId= jsonNode.get("id").asText();
            userInstagramName = jsonNode.get("username").asText();

            System.out.println(userInstagramId);
            System.out.println(userInstagramName);

            return new InstagramUserInfoDTO(userInstagramId, userInstagramName, "https://www.instagram.com/" + userInstagramId, accessToken);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error during Get Instagram UserInformation call : " + e.getMessage());

            return new InstagramUserInfoDTO("", "", "", "");
        }
    }

    /**
     * @요청값: accessToken
     * @리턴값: 정해줘야함 현제 void방식
     * @용도: 단기 accessToken을 장기 accessToken으로 바꿔주는 함수
     * @관련문서: https://developers.facebook.com/docs/instagram-basic-display-api/reference/access_token
     */
    public InstagramAccessTokenDTO longAccessToken(String accessToken) {

        String longAccessToken, tokenType, expiresIn = "";
        String requestUrl = "https://graph.instagram.com/access_token?grant_type=ig_exchange_token&client_secret=" + clientSecret
                + "&access_token=" + accessToken;

        //필요한 것 정의
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(headers);

        //정보 통신, 타임아웃 추가
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        
        ResponseEntity<String> res = restTemplate.exchange(requestUrl, HttpMethod.GET, request, String.class);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(res.getBody());

            longAccessToken = jsonNode.get("access_token").asText();
            tokenType = jsonNode.get("token_type").asText();
            expiresIn = jsonNode.get("expires_in").asText();

            return new InstagramAccessTokenDTO(longAccessToken, tokenType, expiresIn);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error during Get Instagram LongAccessToken : " + e.getMessage());

            return new InstagramAccessTokenDTO("", "", "");
        }
    }

    /**
     * @요청값: longAccessToken
     * @리턴값: longAccessToken(refreshed)
     * @용도: 24시간 이상 된 장기 accessToken을 refresh해주는 함수(24시간이라고 꼭 찝어서 말했으니까, 뭔가 있지 않을까 싶다.)
     * @관련문서: https://developers.facebook.com/docs/instagram-basic-display-api/reference/refresh_access_token
     */
    public InstagramAccessTokenDTO refreshAccessToken(String longAccessToken) {

        String _longAccessToken, tokenType, expiresIn = "";
        String requestUrl = "https://graph.instagram.com/refresh_access_token?grant_type=ig_refresh_token&access_token=" + longAccessToken;

        //필요한 것 정의
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(headers);

        //정보 통신, 타임아웃 추가
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();

        ResponseEntity<String> res = restTemplate.exchange(requestUrl, HttpMethod.GET, request, String.class);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(res.getBody());

            _longAccessToken = jsonNode.get("access_token").asText();
            tokenType = jsonNode.get("token_type").asText();
            expiresIn = jsonNode.get("expires_in").asText();

            return new InstagramAccessTokenDTO(_longAccessToken, tokenType, expiresIn);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error during Refresh Instagram LongAccessToken call : " + e.getMessage());

            return new InstagramAccessTokenDTO("", "", "");
        }

        //저장하는건 기현님이 하신다고 했던거 같으니 패스
    }
}

