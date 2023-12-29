package com.joycity.slg.instagram.oauth;

import lombok.Data;

@Data
public class InstagramUserInfoDTO {
    private String userId = "";
    private String userName = "";
    private String profileUrl = "";
    private String accessToken = "";

    public InstagramUserInfoDTO(String userId, String userName, String profileUrl, String accessToken) {
        this.userId = userId;
        this.userName = userName;
        this.profileUrl = profileUrl;
        this. accessToken = accessToken;
    }
}
