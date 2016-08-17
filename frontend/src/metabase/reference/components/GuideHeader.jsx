import React, { Component, PropTypes } from "react";
import pure from "recompose/pure";
import cx from "classnames";

import S from "./GuideHeader.css";

import EditButton from "metabase/reference/components/EditButton.jsx";

const GuideHeader = ({
    startEditing,
    isSuperuser
}) =>
    <div className={S.guideHeader}>
        <div className={cx("wrapper wrapper--trim", S.guideHeaderBody)}>
            <span className={S.guideHeaderTitle}>Understanding our data</span>
            { isSuperuser &&
                <div className={S.guideHeaderButtons}>
                    <EditButton className={S.guideHeaderEditButton} startEditing={startEditing}/>
                </div>
            }
        </div>
    </div>;
GuideHeader.propTypes = {
    startEditing: PropTypes.func.isRequired,
    isSuperuser: PropTypes.bool
};

export default pure(GuideHeader);
