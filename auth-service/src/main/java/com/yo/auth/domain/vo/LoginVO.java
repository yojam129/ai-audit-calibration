package com.yo.auth.domain.vo;

import com.yo.security.domain.LoginUserVO;
import com.yo.security.domain.TokenPairVO;

public record LoginVO(LoginUserVO user, TokenPairVO tokens) {}
