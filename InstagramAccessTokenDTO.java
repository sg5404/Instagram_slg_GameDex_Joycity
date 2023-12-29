package com.joycity.slg.instagram.oauth;

import lombok.Data;

@Data
public class InstagramAccessTokenDTO {

    private String longAccessToken = "";
    private String tokenType = "";
    private String expiresIn = "";

    public InstagramAccessTokenDTO(String longAccessToken, String tokenType, String expiresIn) {
        this.longAccessToken = longAccessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }
}
