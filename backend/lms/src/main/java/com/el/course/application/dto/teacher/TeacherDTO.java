package com.el.course.application.dto.teacher;

import com.el.common.auth.web.dto.UserInfo;

public record TeacherDTO(
        UserInfo info,
        CountDataDTO count
) {
}
