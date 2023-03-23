/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Code Technology Studio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.jpom.service.dblog;

import cn.hutool.core.util.StrUtil;
import io.jpom.common.ServerConst;
import io.jpom.model.data.RepositoryModel;
import io.jpom.service.h2db.BaseGlobalOrWorkspaceService;
import org.springframework.stereotype.Service;

/**
 * @author Hotstrip
 * Repository service
 */
@Service
public class RepositoryService extends BaseGlobalOrWorkspaceService<RepositoryModel> {

    @Override
    protected void fillSelectResult(RepositoryModel repositoryModel) {
        if (repositoryModel == null) {
            return;
        }
        if (!StrUtil.startWithIgnoreCase(repositoryModel.getPassword(), ServerConst.REF_WORKSPACE_ENV)) {
            // 隐藏密码字段
            repositoryModel.setPassword(null);
        }
        repositoryModel.setRsaPrv(null);
    }
}
