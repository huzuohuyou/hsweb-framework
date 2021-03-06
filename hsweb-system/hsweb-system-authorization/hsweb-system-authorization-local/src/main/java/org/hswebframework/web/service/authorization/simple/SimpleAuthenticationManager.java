package org.hswebframework.web.service.authorization.simple;

import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.AuthenticationInitializeService;
import org.hswebframework.web.authorization.AuthenticationManager;
import org.hswebframework.web.authorization.AuthenticationRequest;
import org.hswebframework.web.authorization.listener.event.AuthorizationFailedEvent;
import org.hswebframework.web.authorization.simple.PlainTextUsernamePasswordAuthenticationRequest;
import org.hswebframework.web.commons.entity.DataStatus;
import org.hswebframework.web.entity.authorization.UserEntity;
import org.hswebframework.web.service.authorization.UserService;
import org.hswebframework.web.validate.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

import java.util.function.Supplier;

/**
 * @author zhouhao
 */
public class SimpleAuthenticationManager implements AuthenticationManager {

    private AuthenticationInitializeService authenticationInitializeService;

    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private CacheManager cacheManager;

    public SimpleAuthenticationManager() {
    }

    public SimpleAuthenticationManager(AuthenticationInitializeService authenticationInitializeService) {
        this.authenticationInitializeService = authenticationInitializeService;
    }

    @Autowired
    public void setAuthenticationInitializeService(AuthenticationInitializeService authenticationInitializeService) {
        this.authenticationInitializeService = authenticationInitializeService;
    }

    @Override
    public Authentication authenticate(AuthenticationRequest request) {
        if (request instanceof PlainTextUsernamePasswordAuthenticationRequest) {
            String username = ((PlainTextUsernamePasswordAuthenticationRequest) request).getUsername();
            String password = ((PlainTextUsernamePasswordAuthenticationRequest) request).getPassword();
            UserEntity userEntity = userService.selectByUserNameAndPassword(username, password);
            if (userEntity == null) {
                throw new ValidationException("密码错误", "password");
            }
            if (!DataStatus.STATUS_ENABLED.equals(userEntity.getStatus())) {
                throw new ValidationException("用户已被禁用", "username");
            }
            return getByUserId(userEntity.getId());
        }
        return null;
    }

    @Override
//    @Cacheable(value = USER_AUTH_CACHE_NAME, key = "#userId")
    public Authentication getByUserId(String userId) {
        Supplier<Authentication> supplier = () -> authenticationInitializeService.initUserAuthorization(userId);

        if (null != cacheManager) {
            Cache cache = cacheManager.getCache(USER_AUTH_CACHE_NAME);
            Cache.ValueWrapper wrapper = cache.get(userId);
            if (wrapper == null) {
                Authentication authentication = supplier.get();
                cache.put(userId, authentication);
                return authentication;
            } else {
                return (Authentication) wrapper.get();
            }
        }
        return supplier.get();
    }

    @Override
    @CachePut(value = USER_AUTH_CACHE_NAME, key = "#authentication.user.id")
    public Authentication sync(Authentication authentication) {
        return authentication;
    }
}
