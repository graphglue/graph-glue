import React, { useEffect } from "react";
import * as platformContentHandler from "./platform-content-handler";
import styleStyle from "!!raw-loader!/styles/style.css"
import mainStyle from "!!raw-loader!/styles/main.css"
import prismStyle from "!!raw-loader!/styles/prism.css"
import { withRouter } from "react-router-dom";

export default withRouter(class DokkaComponent extends React.Component {
    state = {
        contentClickHandler: null
    };

    componentDidMount() {
        const history = this.props.history

        const contentClickHandler = (e) => {
            const targetLink = e.target.closest('a');
            if(!targetLink) return;
            const href = targetLink.getAttribute("href")
            if (!href.startsWith("http")) {
                e.preventDefault();
                history.push(href);
            }
        };

        import("style-scoped").then(() => {
            this.setState({
                contentClickHandler: contentClickHandler
            })
        })
    }

    componentDidUpdate() {
        platformContentHandler.setup()
    }

    render() {
        if (this.state.contentClickHandler) {
            return (<div>
                <style scoped>{styleStyle}</style>
                <style scoped>{mainStyle}</style>
                <style scoped>{prismStyle}</style>
                <div 
                    onClick={this.state.contentClickHandler}
                    dangerouslySetInnerHTML={{__html: this.props.dokkaHTML}}
                />
            </div>)
        } else  {
            return <div/>
        }
    }
})