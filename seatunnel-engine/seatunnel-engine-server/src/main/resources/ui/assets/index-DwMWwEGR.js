import{d as S,h as c,c as Xe,a as Ze,b as le,e as v,f as w,u as q,g as fe,i as y,j as ge,k as s,l as C,N as Ne,m as V,w as Se,r as R,t as oe,n as Je,p as Y,S as Qe,o as E,q as be,s as G,F as eo,v as de,x as oo,y as ae,z as xe,A as ue,B as U,C as X,D as to,E as ro,V as no,G as io,H as I,I as ve,J as lo,K as te,L as re,M as B,O as ao,P as _e,Q as co,R as Q,T as so,U as uo,W as vo}from"./index-iO9FLaCq.js";import{N as ne,o as ho}from"./index-B-Y6fSdB.js";import{u as mo}from"./use-theme-vars-DDYDt6Sq.js";import{l as Te,p as Oe,a as po,b as Me,N as Ae,c as He}from"./LayoutContent-Cj5hafGU.js";import{t as fo,d as go,C as bo,u as he,f as ce,N as xo,a as Co,c as se,b as yo,V as wo,e as zo}from"./service-BXj_OhzB.js";const Io=S({name:"ChevronDownFilled",render(){return c("svg",{viewBox:"0 0 16 16",fill:"none",xmlns:"http://www.w3.org/2000/svg"},c("path",{d:"M3.20041 5.73966C3.48226 5.43613 3.95681 5.41856 4.26034 5.70041L8 9.22652L11.7397 5.70041C12.0432 5.41856 12.5177 5.43613 12.7996 5.73966C13.0815 6.0432 13.0639 6.51775 12.7603 6.7996L8.51034 10.7996C8.22258 11.0668 7.77743 11.0668 7.48967 10.7996L3.23966 6.7996C2.93613 6.51775 2.91856 6.0432 3.20041 5.73966Z",fill:"currentColor"}))}});function ko(e,r,o,n){return{itemColorHoverInverted:"#0000",itemColorActiveInverted:r,itemColorActiveHoverInverted:r,itemColorActiveCollapsedInverted:r,itemTextColorInverted:e,itemTextColorHoverInverted:o,itemTextColorChildActiveInverted:o,itemTextColorChildActiveHoverInverted:o,itemTextColorActiveInverted:o,itemTextColorActiveHoverInverted:o,itemTextColorHorizontalInverted:e,itemTextColorHoverHorizontalInverted:o,itemTextColorChildActiveHorizontalInverted:o,itemTextColorChildActiveHoverHorizontalInverted:o,itemTextColorActiveHorizontalInverted:o,itemTextColorActiveHoverHorizontalInverted:o,itemIconColorInverted:e,itemIconColorHoverInverted:o,itemIconColorActiveInverted:o,itemIconColorActiveHoverInverted:o,itemIconColorChildActiveInverted:o,itemIconColorChildActiveHoverInverted:o,itemIconColorCollapsedInverted:e,itemIconColorHorizontalInverted:e,itemIconColorHoverHorizontalInverted:o,itemIconColorActiveHorizontalInverted:o,itemIconColorActiveHoverHorizontalInverted:o,itemIconColorChildActiveHorizontalInverted:o,itemIconColorChildActiveHoverHorizontalInverted:o,arrowColorInverted:e,arrowColorHoverInverted:o,arrowColorActiveInverted:o,arrowColorActiveHoverInverted:o,arrowColorChildActiveInverted:o,arrowColorChildActiveHoverInverted:o,groupTextColorInverted:n}}function So(e){const{borderRadius:r,textColor3:o,primaryColor:n,textColor2:a,textColor1:l,fontSize:m,dividerColor:h,hoverColor:d,primaryColorHover:A}=e;return Object.assign({borderRadius:r,color:"#0000",groupTextColor:o,itemColorHover:d,itemColorActive:le(n,{alpha:.1}),itemColorActiveHover:le(n,{alpha:.1}),itemColorActiveCollapsed:le(n,{alpha:.1}),itemTextColor:a,itemTextColorHover:a,itemTextColorActive:n,itemTextColorActiveHover:n,itemTextColorChildActive:n,itemTextColorChildActiveHover:n,itemTextColorHorizontal:a,itemTextColorHoverHorizontal:A,itemTextColorActiveHorizontal:n,itemTextColorActiveHoverHorizontal:n,itemTextColorChildActiveHorizontal:n,itemTextColorChildActiveHoverHorizontal:n,itemIconColor:l,itemIconColorHover:l,itemIconColorActive:n,itemIconColorActiveHover:n,itemIconColorChildActive:n,itemIconColorChildActiveHover:n,itemIconColorCollapsed:l,itemIconColorHorizontal:l,itemIconColorHoverHorizontal:A,itemIconColorActiveHorizontal:n,itemIconColorActiveHoverHorizontal:n,itemIconColorChildActiveHorizontal:n,itemIconColorChildActiveHoverHorizontal:n,itemHeight:"42px",arrowColor:a,arrowColorHover:a,arrowColorActive:n,arrowColorActiveHover:n,arrowColorChildActive:n,arrowColorChildActiveHover:n,colorInverted:"#0000",borderColorHorizontal:"#0000",fontSize:m,dividerColor:h},ko("#BBB",n,"#FFF","#AAA"))}const Ao=Xe({name:"Menu",common:Ze,peers:{Tooltip:fo,Dropdown:go},self:So}),Ho=v("layout-header",`
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier),
 box-shadow .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 box-sizing: border-box;
 width: 100%;
 background-color: var(--n-color);
 color: var(--n-text-color);
`,[w("absolute-positioned",`
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 `),w("bordered",`
 border-bottom: solid 1px var(--n-border-color);
 `)]),Ro={position:Oe,inverted:Boolean,bordered:{type:Boolean,default:!1}},Po=S({name:"LayoutHeader",props:Object.assign(Object.assign({},q.props),Ro),setup(e){const{mergedClsPrefixRef:r,inlineThemeDisabled:o}=fe(e),n=q("Layout","-layout-header",Ho,Te,e,r),a=y(()=>{const{common:{cubicBezierEaseInOut:m},self:h}=n.value,d={"--n-bezier":m};return e.inverted?(d["--n-color"]=h.headerColorInverted,d["--n-text-color"]=h.textColorInverted,d["--n-border-color"]=h.headerBorderColorInverted):(d["--n-color"]=h.headerColor,d["--n-text-color"]=h.textColor,d["--n-border-color"]=h.headerBorderColor),d}),l=o?ge("layout-header",y(()=>e.inverted?"a":"b"),a,e):void 0;return{mergedClsPrefix:r,cssVars:o?void 0:a,themeClass:l==null?void 0:l.themeClass,onRender:l==null?void 0:l.onRender}},render(){var e;const{mergedClsPrefix:r}=this;return(e=this.onRender)===null||e===void 0||e.call(this),c("div",{class:[`${r}-layout-header`,this.themeClass,this.position&&`${r}-layout-header--${this.position}-positioned`,this.bordered&&`${r}-layout-header--bordered`],style:this.cssVars},this.$slots)}}),No=v("layout-sider",`
 flex-shrink: 0;
 box-sizing: border-box;
 position: relative;
 z-index: 1;
 color: var(--n-text-color);
 transition:
 color .3s var(--n-bezier),
 border-color .3s var(--n-bezier),
 min-width .3s var(--n-bezier),
 max-width .3s var(--n-bezier),
 transform .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 background-color: var(--n-color);
 display: flex;
 justify-content: flex-end;
`,[w("bordered",[s("border",`
 content: "";
 position: absolute;
 top: 0;
 bottom: 0;
 width: 1px;
 background-color: var(--n-border-color);
 transition: background-color .3s var(--n-bezier);
 `)]),s("left-placement",[w("bordered",[s("border",`
 right: 0;
 `)])]),w("right-placement",`
 justify-content: flex-start;
 `,[w("bordered",[s("border",`
 left: 0;
 `)]),w("collapsed",[v("layout-toggle-button",[v("base-icon",`
 transform: rotate(180deg);
 `)]),v("layout-toggle-bar",[C("&:hover",[s("top",{transform:"rotate(-12deg) scale(1.15) translateY(-2px)"}),s("bottom",{transform:"rotate(12deg) scale(1.15) translateY(2px)"})])])]),v("layout-toggle-button",`
 left: 0;
 transform: translateX(-50%) translateY(-50%);
 `,[v("base-icon",`
 transform: rotate(0);
 `)]),v("layout-toggle-bar",`
 left: -28px;
 transform: rotate(180deg);
 `,[C("&:hover",[s("top",{transform:"rotate(12deg) scale(1.15) translateY(-2px)"}),s("bottom",{transform:"rotate(-12deg) scale(1.15) translateY(2px)"})])])]),w("collapsed",[v("layout-toggle-bar",[C("&:hover",[s("top",{transform:"rotate(-12deg) scale(1.15) translateY(-2px)"}),s("bottom",{transform:"rotate(12deg) scale(1.15) translateY(2px)"})])]),v("layout-toggle-button",[v("base-icon",`
 transform: rotate(0);
 `)])]),v("layout-toggle-button",`
 transition:
 color .3s var(--n-bezier),
 right .3s var(--n-bezier),
 left .3s var(--n-bezier),
 border-color .3s var(--n-bezier),
 background-color .3s var(--n-bezier);
 cursor: pointer;
 width: 24px;
 height: 24px;
 position: absolute;
 top: 50%;
 right: 0;
 border-radius: 50%;
 display: flex;
 align-items: center;
 justify-content: center;
 font-size: 18px;
 color: var(--n-toggle-button-icon-color);
 border: var(--n-toggle-button-border);
 background-color: var(--n-toggle-button-color);
 box-shadow: 0 2px 4px 0px rgba(0, 0, 0, .06);
 transform: translateX(50%) translateY(-50%);
 z-index: 1;
 `,[v("base-icon",`
 transition: transform .3s var(--n-bezier);
 transform: rotate(180deg);
 `)]),v("layout-toggle-bar",`
 cursor: pointer;
 height: 72px;
 width: 32px;
 position: absolute;
 top: calc(50% - 36px);
 right: -28px;
 `,[s("top, bottom",`
 position: absolute;
 width: 4px;
 border-radius: 2px;
 height: 38px;
 left: 14px;
 transition: 
 background-color .3s var(--n-bezier),
 transform .3s var(--n-bezier);
 `),s("bottom",`
 position: absolute;
 top: 34px;
 `),C("&:hover",[s("top",{transform:"rotate(12deg) scale(1.15) translateY(-2px)"}),s("bottom",{transform:"rotate(-12deg) scale(1.15) translateY(2px)"})]),s("top, bottom",{backgroundColor:"var(--n-toggle-bar-color)"}),C("&:hover",[s("top, bottom",{backgroundColor:"var(--n-toggle-bar-color-hover)"})])]),s("border",`
 position: absolute;
 top: 0;
 right: 0;
 bottom: 0;
 width: 1px;
 transition: background-color .3s var(--n-bezier);
 `),v("layout-sider-scroll-container",`
 flex-grow: 1;
 flex-shrink: 0;
 box-sizing: border-box;
 height: 100%;
 opacity: 0;
 transition: opacity .3s var(--n-bezier);
 max-width: 100%;
 `),w("show-content",[v("layout-sider-scroll-container",{opacity:1})]),w("absolute-positioned",`
 position: absolute;
 left: 0;
 top: 0;
 bottom: 0;
 `)]),_o=S({name:"LayoutToggleButton",props:{clsPrefix:{type:String,required:!0},onClick:Function},render(){const{clsPrefix:e}=this;return c("div",{class:`${e}-layout-toggle-button`,onClick:this.onClick},c(Ne,{clsPrefix:e},{default:()=>c(bo,null)}))}}),To=S({props:{clsPrefix:{type:String,required:!0},onClick:Function},render(){const{clsPrefix:e}=this;return c("div",{onClick:this.onClick,class:`${e}-layout-toggle-bar`},c("div",{class:`${e}-layout-toggle-bar__top`}),c("div",{class:`${e}-layout-toggle-bar__bottom`}))}}),Oo={position:Oe,bordered:Boolean,collapsedWidth:{type:Number,default:48},width:{type:[Number,String],default:272},contentClass:String,contentStyle:{type:[String,Object],default:""},collapseMode:{type:String,default:"transform"},collapsed:{type:Boolean,default:void 0},defaultCollapsed:Boolean,showCollapsedContent:{type:Boolean,default:!0},showTrigger:{type:[Boolean,String],default:!1},nativeScrollbar:{type:Boolean,default:!0},inverted:Boolean,scrollbarProps:Object,triggerClass:String,triggerStyle:[String,Object],collapsedTriggerClass:String,collapsedTriggerStyle:[String,Object],"onUpdate:collapsed":[Function,Array],onUpdateCollapsed:[Function,Array],onAfterEnter:Function,onAfterLeave:Function,onExpand:[Function,Array],onCollapse:[Function,Array],onScroll:Function},Mo=S({name:"LayoutSider",props:Object.assign(Object.assign({},q.props),Oo),setup(e){const r=V(po);r?r.hasSider||Se("layout-sider","You are putting `n-layout-sider` in a `n-layout` but haven't set `has-sider` on the `n-layout`."):Se("layout-sider","Layout sider is not allowed to be put outside layout.");const o=R(null),n=R(null),a=R(e.defaultCollapsed),l=he(oe(e,"collapsed"),a),m=y(()=>ce(l.value?e.collapsedWidth:e.width)),h=y(()=>e.collapseMode!=="transform"?{}:{minWidth:ce(e.width)}),d=y(()=>r?r.siderPlacement:"left");function A(k,b){if(e.nativeScrollbar){const{value:x}=o;x&&(b===void 0?x.scrollTo(k):x.scrollTo(k,b))}else{const{value:x}=n;x&&x.scrollTo(k,b)}}function M(){const{"onUpdate:collapsed":k,onUpdateCollapsed:b,onExpand:x,onCollapse:K}=e,{value:F}=l;b&&E(b,!F),k&&E(k,!F),a.value=!F,F?x&&E(x):K&&E(K)}let H=0,f=0;const N=k=>{var b;const x=k.target;H=x.scrollLeft,f=x.scrollTop,(b=e.onScroll)===null||b===void 0||b.call(e,k)};Je(()=>{if(e.nativeScrollbar){const k=o.value;k&&(k.scrollTop=f,k.scrollLeft=H)}}),Y(Me,{collapsedRef:l,collapseModeRef:oe(e,"collapseMode")});const{mergedClsPrefixRef:_,inlineThemeDisabled:P}=fe(e),O=q("Layout","-layout-sider",No,Te,e,_);function j(k){var b,x;k.propertyName==="max-width"&&(l.value?(b=e.onAfterLeave)===null||b===void 0||b.call(e):(x=e.onAfterEnter)===null||x===void 0||x.call(e))}const W={scrollTo:A},L=y(()=>{const{common:{cubicBezierEaseInOut:k},self:b}=O.value,{siderToggleButtonColor:x,siderToggleButtonBorder:K,siderToggleBarColor:F,siderToggleBarColorHover:ie}=b,T={"--n-bezier":k,"--n-toggle-button-color":x,"--n-toggle-button-border":K,"--n-toggle-bar-color":F,"--n-toggle-bar-color-hover":ie};return e.inverted?(T["--n-color"]=b.siderColorInverted,T["--n-text-color"]=b.textColorInverted,T["--n-border-color"]=b.siderBorderColorInverted,T["--n-toggle-button-icon-color"]=b.siderToggleButtonIconColorInverted,T.__invertScrollbar=b.__invertScrollbar):(T["--n-color"]=b.siderColor,T["--n-text-color"]=b.textColor,T["--n-border-color"]=b.siderBorderColor,T["--n-toggle-button-icon-color"]=b.siderToggleButtonIconColor),T}),$=P?ge("layout-sider",y(()=>e.inverted?"a":"b"),L,e):void 0;return Object.assign({scrollableElRef:o,scrollbarInstRef:n,mergedClsPrefix:_,mergedTheme:O,styleMaxWidth:m,mergedCollapsed:l,scrollContainerStyle:h,siderPlacement:d,handleNativeElScroll:N,handleTransitionend:j,handleTriggerClick:M,inlineThemeDisabled:P,cssVars:L,themeClass:$==null?void 0:$.themeClass,onRender:$==null?void 0:$.onRender},W)},render(){var e;const{mergedClsPrefix:r,mergedCollapsed:o,showTrigger:n}=this;return(e=this.onRender)===null||e===void 0||e.call(this),c("aside",{class:[`${r}-layout-sider`,this.themeClass,`${r}-layout-sider--${this.position}-positioned`,`${r}-layout-sider--${this.siderPlacement}-placement`,this.bordered&&`${r}-layout-sider--bordered`,o&&`${r}-layout-sider--collapsed`,(!o||this.showCollapsedContent)&&`${r}-layout-sider--show-content`],onTransitionend:this.handleTransitionend,style:[this.inlineThemeDisabled?void 0:this.cssVars,{maxWidth:this.styleMaxWidth,width:ce(this.width)}]},this.nativeScrollbar?c("div",{class:[`${r}-layout-sider-scroll-container`,this.contentClass],onScroll:this.handleNativeElScroll,style:[this.scrollContainerStyle,{overflow:"auto"},this.contentStyle],ref:"scrollableElRef"},this.$slots):c(Qe,Object.assign({},this.scrollbarProps,{onScroll:this.onScroll,ref:"scrollbarInstRef",style:this.scrollContainerStyle,contentStyle:this.contentStyle,contentClass:this.contentClass,theme:this.mergedTheme.peers.Scrollbar,themeOverrides:this.mergedTheme.peerOverrides.Scrollbar,builtinThemeOverrides:this.inverted&&this.cssVars.__invertScrollbar==="true"?{colorHover:"rgba(255, 255, 255, .4)",color:"rgba(255, 255, 255, .3)"}:void 0}),this.$slots),n?n==="bar"?c(To,{clsPrefix:r,class:o?this.collapsedTriggerClass:this.triggerClass,style:o?this.collapsedTriggerStyle:this.triggerStyle,onClick:this.handleTriggerClick}):c(_o,{clsPrefix:r,class:o?this.collapsedTriggerClass:this.triggerClass,style:o?this.collapsedTriggerStyle:this.triggerStyle,onClick:this.handleTriggerClick}):null,this.bordered?c("div",{class:`${r}-layout-sider__border`}):null)}}),Z=be("n-menu"),Ce=be("n-submenu"),ye=be("n-menu-item-group"),ee=8;function we(e){const r=V(Z),{props:o,mergedCollapsedRef:n}=r,a=V(Ce,null),l=V(ye,null),m=y(()=>o.mode==="horizontal"),h=y(()=>m.value?o.dropdownPlacement:"tmNodes"in e?"right-start":"right"),d=y(()=>{var f;return Math.max((f=o.collapsedIconSize)!==null&&f!==void 0?f:o.iconSize,o.iconSize)}),A=y(()=>{var f;return!m.value&&e.root&&n.value&&(f=o.collapsedIconSize)!==null&&f!==void 0?f:o.iconSize}),M=y(()=>{if(m.value)return;const{collapsedWidth:f,indent:N,rootIndent:_}=o,{root:P,isGroup:O}=e,j=_===void 0?N:_;return P?n.value?f/2-d.value/2:j:l&&typeof l.paddingLeftRef.value=="number"?N/2+l.paddingLeftRef.value:a&&typeof a.paddingLeftRef.value=="number"?(O?N/2:N)+a.paddingLeftRef.value:0}),H=y(()=>{const{collapsedWidth:f,indent:N,rootIndent:_}=o,{value:P}=d,{root:O}=e;return m.value||!O||!n.value?ee:(_===void 0?N:_)+P+ee-(f+P)/2});return{dropdownPlacement:h,activeIconSize:A,maxIconSize:d,paddingLeft:M,iconMarginRight:H,NMenu:r,NSubmenu:a}}const ze={internalKey:{type:[String,Number],required:!0},root:Boolean,isGroup:Boolean,level:{type:Number,required:!0},title:[String,Function],extra:[String,Function]},$e=Object.assign(Object.assign({},ze),{tmNode:{type:Object,required:!0},tmNodes:{type:Array,required:!0}}),$o=S({name:"MenuOptionGroup",props:$e,setup(e){Y(Ce,null);const r=we(e);Y(ye,{paddingLeftRef:r.paddingLeft});const{mergedClsPrefixRef:o,props:n}=V(Z);return function(){const{value:a}=o,l=r.paddingLeft.value,{nodeProps:m}=n,h=m==null?void 0:m(e.tmNode.rawNode);return c("div",{class:`${a}-menu-item-group`,role:"group"},c("div",Object.assign({},h,{class:[`${a}-menu-item-group-title`,h==null?void 0:h.class],style:[(h==null?void 0:h.style)||"",l!==void 0?`padding-left: ${l}px;`:""]}),G(e.title),e.extra?c(eo,null," ",G(e.extra)):null),c("div",null,e.tmNodes.map(d=>Ie(d,n))))}}}),Ee=S({name:"MenuOptionContent",props:{collapsed:Boolean,disabled:Boolean,title:[String,Function],icon:Function,extra:[String,Function],showArrow:Boolean,childActive:Boolean,hover:Boolean,paddingLeft:Number,selected:Boolean,maxIconSize:{type:Number,required:!0},activeIconSize:{type:Number,required:!0},iconMarginRight:{type:Number,required:!0},clsPrefix:{type:String,required:!0},onClick:Function,tmNode:{type:Object,required:!0},isEllipsisPlaceholder:Boolean},setup(e){const{props:r}=V(Z);return{menuProps:r,style:y(()=>{const{paddingLeft:o}=e;return{paddingLeft:o&&`${o}px`}}),iconStyle:y(()=>{const{maxIconSize:o,activeIconSize:n,iconMarginRight:a}=e;return{width:`${o}px`,height:`${o}px`,fontSize:`${n}px`,marginRight:`${a}px`}})}},render(){const{clsPrefix:e,tmNode:r,menuProps:{renderIcon:o,renderLabel:n,renderExtra:a,expandIcon:l}}=this,m=o?o(r.rawNode):G(this.icon);return c("div",{onClick:h=>{var d;(d=this.onClick)===null||d===void 0||d.call(this,h)},role:"none",class:[`${e}-menu-item-content`,{[`${e}-menu-item-content--selected`]:this.selected,[`${e}-menu-item-content--collapsed`]:this.collapsed,[`${e}-menu-item-content--child-active`]:this.childActive,[`${e}-menu-item-content--disabled`]:this.disabled,[`${e}-menu-item-content--hover`]:this.hover}],style:this.style},m&&c("div",{class:`${e}-menu-item-content__icon`,style:this.iconStyle,role:"none"},[m]),c("div",{class:`${e}-menu-item-content-header`,role:"none"},this.isEllipsisPlaceholder?this.title:n?n(r.rawNode):G(this.title),this.extra||a?c("span",{class:`${e}-menu-item-content-header__extra`}," ",a?a(r.rawNode):G(this.extra)):null),this.showArrow?c(Ne,{ariaHidden:!0,class:`${e}-menu-item-content__arrow`,clsPrefix:e},{default:()=>l?l(r.rawNode):c(Io,null)}):null)}}),je=Object.assign(Object.assign({},ze),{rawNodes:{type:Array,default:()=>[]},tmNodes:{type:Array,default:()=>[]},tmNode:{type:Object,required:!0},disabled:Boolean,icon:Function,onClick:Function,domId:String,virtualChildActive:{type:Boolean,default:void 0},isEllipsisPlaceholder:Boolean}),me=S({name:"Submenu",props:je,setup(e){const r=we(e),{NMenu:o,NSubmenu:n}=r,{props:a,mergedCollapsedRef:l,mergedThemeRef:m}=o,h=y(()=>{const{disabled:f}=e;return n!=null&&n.mergedDisabledRef.value||a.disabled?!0:f}),d=R(!1);Y(Ce,{paddingLeftRef:r.paddingLeft,mergedDisabledRef:h}),Y(ye,null);function A(){const{onClick:f}=e;f&&f()}function M(){h.value||(l.value||o.toggleExpand(e.internalKey),A())}function H(f){d.value=f}return{menuProps:a,mergedTheme:m,doSelect:o.doSelect,inverted:o.invertedRef,isHorizontal:o.isHorizontalRef,mergedClsPrefix:o.mergedClsPrefixRef,maxIconSize:r.maxIconSize,activeIconSize:r.activeIconSize,iconMarginRight:r.iconMarginRight,dropdownPlacement:r.dropdownPlacement,dropdownShow:d,paddingLeft:r.paddingLeft,mergedDisabled:h,mergedValue:o.mergedValueRef,childActive:de(()=>{var f;return(f=e.virtualChildActive)!==null&&f!==void 0?f:o.activePathRef.value.includes(e.internalKey)}),collapsed:y(()=>a.mode==="horizontal"?!1:l.value?!0:!o.mergedExpandedKeysRef.value.includes(e.internalKey)),dropdownEnabled:y(()=>!h.value&&(a.mode==="horizontal"||l.value)),handlePopoverShowChange:H,handleClick:M}},render(){var e;const{mergedClsPrefix:r,menuProps:{renderIcon:o,renderLabel:n}}=this,a=()=>{const{isHorizontal:m,paddingLeft:h,collapsed:d,mergedDisabled:A,maxIconSize:M,activeIconSize:H,title:f,childActive:N,icon:_,handleClick:P,menuProps:{nodeProps:O},dropdownShow:j,iconMarginRight:W,tmNode:L,mergedClsPrefix:$,isEllipsisPlaceholder:k,extra:b}=this,x=O==null?void 0:O(L.rawNode);return c("div",Object.assign({},x,{class:[`${$}-menu-item`,x==null?void 0:x.class],role:"menuitem"}),c(Ee,{tmNode:L,paddingLeft:h,collapsed:d,disabled:A,iconMarginRight:W,maxIconSize:M,activeIconSize:H,title:f,extra:b,showArrow:!m,childActive:N,clsPrefix:$,icon:_,hover:j,onClick:P,isEllipsisPlaceholder:k}))},l=()=>c(oo,null,{default:()=>{const{tmNodes:m,collapsed:h}=this;return h?null:c("div",{class:`${r}-submenu-children`,role:"menu"},m.map(d=>Ie(d,this.menuProps)))}});return this.root?c(xo,Object.assign({size:"large",trigger:"hover"},(e=this.menuProps)===null||e===void 0?void 0:e.dropdownProps,{themeOverrides:this.mergedTheme.peerOverrides.Dropdown,theme:this.mergedTheme.peers.Dropdown,builtinThemeOverrides:{fontSizeLarge:"14px",optionIconSizeLarge:"18px"},value:this.mergedValue,disabled:!this.dropdownEnabled,placement:this.dropdownPlacement,keyField:this.menuProps.keyField,labelField:this.menuProps.labelField,childrenField:this.menuProps.childrenField,onUpdateShow:this.handlePopoverShowChange,options:this.rawNodes,onSelect:this.doSelect,inverted:this.inverted,renderIcon:o,renderLabel:n}),{default:()=>c("div",{class:`${r}-submenu`,role:"menu","aria-expanded":!this.collapsed,id:this.domId},a(),this.isHorizontal?null:l())}):c("div",{class:`${r}-submenu`,role:"menu","aria-expanded":!this.collapsed,id:this.domId},a(),l())}}),Be=Object.assign(Object.assign({},ze),{tmNode:{type:Object,required:!0},disabled:Boolean,icon:Function,onClick:Function}),Eo=S({name:"MenuOption",props:Be,setup(e){const r=we(e),{NSubmenu:o,NMenu:n}=r,{props:a,mergedClsPrefixRef:l,mergedCollapsedRef:m}=n,h=o?o.mergedDisabledRef:{value:!1},d=y(()=>h.value||e.disabled);function A(H){const{onClick:f}=e;f&&f(H)}function M(H){d.value||(n.doSelect(e.internalKey,e.tmNode.rawNode),A(H))}return{mergedClsPrefix:l,dropdownPlacement:r.dropdownPlacement,paddingLeft:r.paddingLeft,iconMarginRight:r.iconMarginRight,maxIconSize:r.maxIconSize,activeIconSize:r.activeIconSize,mergedTheme:n.mergedThemeRef,menuProps:a,dropdownEnabled:de(()=>e.root&&m.value&&a.mode!=="horizontal"&&!d.value),selected:de(()=>n.mergedValueRef.value===e.internalKey),mergedDisabled:d,handleClick:M}},render(){const{mergedClsPrefix:e,mergedTheme:r,tmNode:o,menuProps:{renderLabel:n,nodeProps:a}}=this,l=a==null?void 0:a(o.rawNode);return c("div",Object.assign({},l,{role:"menuitem",class:[`${e}-menu-item`,l==null?void 0:l.class]}),c(Co,{theme:r.peers.Tooltip,themeOverrides:r.peerOverrides.Tooltip,trigger:"hover",placement:this.dropdownPlacement,disabled:!this.dropdownEnabled||this.title===void 0,internalExtraClass:["menu-tooltip"]},{default:()=>n?n(o.rawNode):G(this.title),trigger:()=>c(Ee,{tmNode:o,clsPrefix:e,paddingLeft:this.paddingLeft,iconMarginRight:this.iconMarginRight,maxIconSize:this.maxIconSize,activeIconSize:this.activeIconSize,selected:this.selected,title:this.title,extra:this.extra,disabled:this.mergedDisabled,icon:this.icon,onClick:this.handleClick})}))}}),jo=S({name:"MenuDivider",setup(){const e=V(Z),{mergedClsPrefixRef:r,isHorizontalRef:o}=e;return()=>o.value?null:c("div",{class:`${r.value}-menu-divider`})}}),Bo=xe($e),Fo=xe(Be),Lo=xe(je);function pe(e){return e.type==="divider"||e.type==="render"}function Ko(e){return e.type==="divider"}function Ie(e,r){const{rawNode:o}=e,{show:n}=o;if(n===!1)return null;if(pe(o))return Ko(o)?c(jo,Object.assign({key:e.key},o.props)):null;const{labelField:a}=r,{key:l,level:m,isGroup:h}=e,d=Object.assign(Object.assign({},o),{title:o.title||o[a],extra:o.titleExtra||o.extra,key:l,internalKey:l,level:m,root:m===0,isGroup:h});return e.children?e.isGroup?c($o,ae(d,Bo,{tmNode:e,tmNodes:e.children,key:l})):c(me,ae(d,Lo,{key:l,rawNodes:o[r.childrenField],tmNodes:e.children,tmNode:e})):c(Eo,ae(d,Fo,{key:l,tmNode:e}))}function Vo(e){ue(()=>{e.items&&U("menu","`items` is deprecated, please use `options` instead."),e.onOpenNamesChange&&U("menu","`on-open-names-change` is deprecated, please use `on-update:expanded-keys` instead."),e.onSelect&&U("menu","`on-select` is deprecated, please use `on-update:value` instead."),e.onExpandedNamesChange&&U("menu","`on-expanded-names-change` is deprecated, please use `on-update:expanded-keys` instead."),e.expandedNames&&U("menu","`expanded-names` is deprecated, please use `expanded-keys` instead."),e.defaultExpandedNames&&U("menu","`default-expanded-names` is deprecated, please use `default-expanded-keys` instead.")})}const Re=[C("&::before","background-color: var(--n-item-color-hover);"),s("arrow",`
 color: var(--n-arrow-color-hover);
 `),s("icon",`
 color: var(--n-item-icon-color-hover);
 `),v("menu-item-content-header",`
 color: var(--n-item-text-color-hover);
 `,[C("a",`
 color: var(--n-item-text-color-hover);
 `),s("extra",`
 color: var(--n-item-text-color-hover);
 `)])],Pe=[s("icon",`
 color: var(--n-item-icon-color-hover-horizontal);
 `),v("menu-item-content-header",`
 color: var(--n-item-text-color-hover-horizontal);
 `,[C("a",`
 color: var(--n-item-text-color-hover-horizontal);
 `),s("extra",`
 color: var(--n-item-text-color-hover-horizontal);
 `)])],Do=C([v("menu",`
 background-color: var(--n-color);
 color: var(--n-item-text-color);
 overflow: hidden;
 transition: background-color .3s var(--n-bezier);
 box-sizing: border-box;
 font-size: var(--n-font-size);
 padding-bottom: 6px;
 `,[w("horizontal",`
 max-width: 100%;
 width: 100%;
 display: flex;
 overflow: hidden;
 padding-bottom: 0;
 `,[v("submenu","margin: 0;"),v("menu-item","margin: 0;"),v("menu-item-content",`
 padding: 0 20px;
 border-bottom: 2px solid #0000;
 `,[C("&::before","display: none;"),w("selected","border-bottom: 2px solid var(--n-border-color-horizontal)")]),v("menu-item-content",[w("selected",[s("icon","color: var(--n-item-icon-color-active-horizontal);"),v("menu-item-content-header",`
 color: var(--n-item-text-color-active-horizontal);
 `,[C("a","color: var(--n-item-text-color-active-horizontal);"),s("extra","color: var(--n-item-text-color-active-horizontal);")])]),w("child-active",`
 border-bottom: 2px solid var(--n-border-color-horizontal);
 `,[v("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-horizontal);
 `,[C("a",`
 color: var(--n-item-text-color-child-active-horizontal);
 `),s("extra",`
 color: var(--n-item-text-color-child-active-horizontal);
 `)]),s("icon",`
 color: var(--n-item-icon-color-child-active-horizontal);
 `)]),X("disabled",[X("selected, child-active",[C("&:focus-within",Pe)]),w("selected",[D(null,[s("icon","color: var(--n-item-icon-color-active-hover-horizontal);"),v("menu-item-content-header",`
 color: var(--n-item-text-color-active-hover-horizontal);
 `,[C("a","color: var(--n-item-text-color-active-hover-horizontal);"),s("extra","color: var(--n-item-text-color-active-hover-horizontal);")])])]),w("child-active",[D(null,[s("icon","color: var(--n-item-icon-color-child-active-hover-horizontal);"),v("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-hover-horizontal);
 `,[C("a","color: var(--n-item-text-color-child-active-hover-horizontal);"),s("extra","color: var(--n-item-text-color-child-active-hover-horizontal);")])])]),D("border-bottom: 2px solid var(--n-border-color-horizontal);",Pe)]),v("menu-item-content-header",[C("a","color: var(--n-item-text-color-horizontal);")])])]),X("responsive",[v("menu-item-content-header",`
 overflow: hidden;
 text-overflow: ellipsis;
 `)]),w("collapsed",[v("menu-item-content",[w("selected",[C("&::before",`
 background-color: var(--n-item-color-active-collapsed) !important;
 `)]),v("menu-item-content-header","opacity: 0;"),s("arrow","opacity: 0;"),s("icon","color: var(--n-item-icon-color-collapsed);")])]),v("menu-item",`
 height: var(--n-item-height);
 margin-top: 6px;
 position: relative;
 `),v("menu-item-content",`
 box-sizing: border-box;
 line-height: 1.75;
 height: 100%;
 display: grid;
 grid-template-areas: "icon content arrow";
 grid-template-columns: auto 1fr auto;
 align-items: center;
 cursor: pointer;
 position: relative;
 padding-right: 18px;
 transition:
 background-color .3s var(--n-bezier),
 padding-left .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 `,[C("> *","z-index: 1;"),C("&::before",`
 z-index: auto;
 content: "";
 background-color: #0000;
 position: absolute;
 left: 8px;
 right: 8px;
 top: 0;
 bottom: 0;
 pointer-events: none;
 border-radius: var(--n-border-radius);
 transition: background-color .3s var(--n-bezier);
 `),w("disabled",`
 opacity: .45;
 cursor: not-allowed;
 `),w("collapsed",[s("arrow","transform: rotate(0);")]),w("selected",[C("&::before","background-color: var(--n-item-color-active);"),s("arrow","color: var(--n-arrow-color-active);"),s("icon","color: var(--n-item-icon-color-active);"),v("menu-item-content-header",`
 color: var(--n-item-text-color-active);
 `,[C("a","color: var(--n-item-text-color-active);"),s("extra","color: var(--n-item-text-color-active);")])]),w("child-active",[v("menu-item-content-header",`
 color: var(--n-item-text-color-child-active);
 `,[C("a",`
 color: var(--n-item-text-color-child-active);
 `),s("extra",`
 color: var(--n-item-text-color-child-active);
 `)]),s("arrow",`
 color: var(--n-arrow-color-child-active);
 `),s("icon",`
 color: var(--n-item-icon-color-child-active);
 `)]),X("disabled",[X("selected, child-active",[C("&:focus-within",Re)]),w("selected",[D(null,[s("arrow","color: var(--n-arrow-color-active-hover);"),s("icon","color: var(--n-item-icon-color-active-hover);"),v("menu-item-content-header",`
 color: var(--n-item-text-color-active-hover);
 `,[C("a","color: var(--n-item-text-color-active-hover);"),s("extra","color: var(--n-item-text-color-active-hover);")])])]),w("child-active",[D(null,[s("arrow","color: var(--n-arrow-color-child-active-hover);"),s("icon","color: var(--n-item-icon-color-child-active-hover);"),v("menu-item-content-header",`
 color: var(--n-item-text-color-child-active-hover);
 `,[C("a","color: var(--n-item-text-color-child-active-hover);"),s("extra","color: var(--n-item-text-color-child-active-hover);")])])]),w("selected",[D(null,[C("&::before","background-color: var(--n-item-color-active-hover);")])]),D(null,Re)]),s("icon",`
 grid-area: icon;
 color: var(--n-item-icon-color);
 transition:
 color .3s var(--n-bezier),
 font-size .3s var(--n-bezier),
 margin-right .3s var(--n-bezier);
 box-sizing: content-box;
 display: inline-flex;
 align-items: center;
 justify-content: center;
 `),s("arrow",`
 grid-area: arrow;
 font-size: 16px;
 color: var(--n-arrow-color);
 transform: rotate(180deg);
 opacity: 1;
 transition:
 color .3s var(--n-bezier),
 transform 0.2s var(--n-bezier),
 opacity 0.2s var(--n-bezier);
 `),v("menu-item-content-header",`
 grid-area: content;
 transition:
 color .3s var(--n-bezier),
 opacity .3s var(--n-bezier);
 opacity: 1;
 white-space: nowrap;
 color: var(--n-item-text-color);
 `,[C("a",`
 outline: none;
 text-decoration: none;
 transition: color .3s var(--n-bezier);
 color: var(--n-item-text-color);
 `,[C("&::before",`
 content: "";
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 `)]),s("extra",`
 font-size: .93em;
 color: var(--n-group-text-color);
 transition: color .3s var(--n-bezier);
 `)])]),v("submenu",`
 cursor: pointer;
 position: relative;
 margin-top: 6px;
 `,[v("menu-item-content",`
 height: var(--n-item-height);
 `),v("submenu-children",`
 overflow: hidden;
 padding: 0;
 `,[to({duration:".2s"})])]),v("menu-item-group",[v("menu-item-group-title",`
 margin-top: 6px;
 color: var(--n-group-text-color);
 cursor: default;
 font-size: .93em;
 height: 36px;
 display: flex;
 align-items: center;
 transition:
 padding-left .3s var(--n-bezier),
 color .3s var(--n-bezier);
 `)])]),v("menu-tooltip",[C("a",`
 color: inherit;
 text-decoration: none;
 `)]),v("menu-divider",`
 transition: background-color .3s var(--n-bezier);
 background-color: var(--n-divider-color);
 height: 1px;
 margin: 6px 18px;
 `)]);function D(e,r){return[w("hover",e,r),C("&:hover",e,r)]}const Uo=Object.assign(Object.assign({},q.props),{options:{type:Array,default:()=>[]},collapsed:{type:Boolean,default:void 0},collapsedWidth:{type:Number,default:48},iconSize:{type:Number,default:20},collapsedIconSize:{type:Number,default:24},rootIndent:Number,indent:{type:Number,default:32},labelField:{type:String,default:"label"},keyField:{type:String,default:"key"},childrenField:{type:String,default:"children"},disabledField:{type:String,default:"disabled"},defaultExpandAll:Boolean,defaultExpandedKeys:Array,expandedKeys:Array,value:[String,Number],defaultValue:{type:[String,Number],default:null},mode:{type:String,default:"vertical"},watchProps:{type:Array,default:void 0},disabled:Boolean,show:{type:Boolean,default:!0},inverted:Boolean,"onUpdate:expandedKeys":[Function,Array],onUpdateExpandedKeys:[Function,Array],onUpdateValue:[Function,Array],"onUpdate:value":[Function,Array],expandIcon:Function,renderIcon:Function,renderLabel:Function,renderExtra:Function,dropdownProps:Object,accordion:Boolean,nodeProps:Function,dropdownPlacement:{type:String,default:"bottom"},responsive:Boolean,items:Array,onOpenNamesChange:[Function,Array],onSelect:[Function,Array],onExpandedNamesChange:[Function,Array],expandedNames:Array,defaultExpandedNames:Array}),Go=S({name:"Menu",inheritAttrs:!1,props:Uo,setup(e){Vo(e);const{mergedClsPrefixRef:r,inlineThemeDisabled:o}=fe(e),n=q("Menu","-menu",Do,Ao,e,r),a=V(Me,null),l=y(()=>{var u;const{collapsed:g}=e;if(g!==void 0)return g;if(a){const{collapseModeRef:t,collapsedRef:p}=a;if(t.value==="width")return(u=p.value)!==null&&u!==void 0?u:!1}return!1}),m=y(()=>{const{keyField:u,childrenField:g,disabledField:t}=e;return se(e.items||e.options,{getIgnored(p){return pe(p)},getChildren(p){return p[g]},getDisabled(p){return p[t]},getKey(p){var z;return(z=p[u])!==null&&z!==void 0?z:p.name}})}),h=y(()=>new Set(m.value.treeNodes.map(u=>u.key))),{watchProps:d}=e,A=R(null);d!=null&&d.includes("defaultValue")?ue(()=>{A.value=e.defaultValue}):A.value=e.defaultValue;const M=oe(e,"value"),H=he(M,A),f=R([]),N=()=>{f.value=e.defaultExpandAll?m.value.getNonLeafKeys():e.defaultExpandedNames||e.defaultExpandedKeys||m.value.getPath(H.value,{includeSelf:!1}).keyPath};d!=null&&d.includes("defaultExpandedKeys")?ue(N):N();const _=yo(e,["expandedNames","expandedKeys"]),P=he(_,f),O=y(()=>m.value.treeNodes),j=y(()=>m.value.getPath(H.value).keyPath);Y(Z,{props:e,mergedCollapsedRef:l,mergedThemeRef:n,mergedValueRef:H,mergedExpandedKeysRef:P,activePathRef:j,mergedClsPrefixRef:r,isHorizontalRef:y(()=>e.mode==="horizontal"),invertedRef:oe(e,"inverted"),doSelect:W,toggleExpand:$});function W(u,g){const{"onUpdate:value":t,onUpdateValue:p,onSelect:z}=e;p&&E(p,u,g),t&&E(t,u,g),z&&E(z,u,g),A.value=u}function L(u){const{"onUpdate:expandedKeys":g,onUpdateExpandedKeys:t,onExpandedNamesChange:p,onOpenNamesChange:z}=e;g&&E(g,u),t&&E(t,u),p&&E(p,u),z&&E(z,u),f.value=u}function $(u){const g=Array.from(P.value),t=g.findIndex(p=>p===u);if(~t)g.splice(t,1);else{if(e.accordion&&h.value.has(u)){const p=g.findIndex(z=>h.value.has(z));p>-1&&g.splice(p,1)}g.push(u)}L(g)}const k=u=>{const g=m.value.getPath(u??H.value,{includeSelf:!1}).keyPath;if(!g.length)return;const t=Array.from(P.value),p=new Set([...t,...g]);e.accordion&&h.value.forEach(z=>{p.has(z)&&!g.includes(z)&&p.delete(z)}),L(Array.from(p))},b=y(()=>{const{inverted:u}=e,{common:{cubicBezierEaseInOut:g},self:t}=n.value,{borderRadius:p,borderColorHorizontal:z,fontSize:qe,itemHeight:Ye,dividerColor:We}=t,i={"--n-divider-color":We,"--n-bezier":g,"--n-font-size":qe,"--n-border-color-horizontal":z,"--n-border-radius":p,"--n-item-height":Ye};return u?(i["--n-group-text-color"]=t.groupTextColorInverted,i["--n-color"]=t.colorInverted,i["--n-item-text-color"]=t.itemTextColorInverted,i["--n-item-text-color-hover"]=t.itemTextColorHoverInverted,i["--n-item-text-color-active"]=t.itemTextColorActiveInverted,i["--n-item-text-color-child-active"]=t.itemTextColorChildActiveInverted,i["--n-item-text-color-child-active-hover"]=t.itemTextColorChildActiveInverted,i["--n-item-text-color-active-hover"]=t.itemTextColorActiveHoverInverted,i["--n-item-icon-color"]=t.itemIconColorInverted,i["--n-item-icon-color-hover"]=t.itemIconColorHoverInverted,i["--n-item-icon-color-active"]=t.itemIconColorActiveInverted,i["--n-item-icon-color-active-hover"]=t.itemIconColorActiveHoverInverted,i["--n-item-icon-color-child-active"]=t.itemIconColorChildActiveInverted,i["--n-item-icon-color-child-active-hover"]=t.itemIconColorChildActiveHoverInverted,i["--n-item-icon-color-collapsed"]=t.itemIconColorCollapsedInverted,i["--n-item-text-color-horizontal"]=t.itemTextColorHorizontalInverted,i["--n-item-text-color-hover-horizontal"]=t.itemTextColorHoverHorizontalInverted,i["--n-item-text-color-active-horizontal"]=t.itemTextColorActiveHorizontalInverted,i["--n-item-text-color-child-active-horizontal"]=t.itemTextColorChildActiveHorizontalInverted,i["--n-item-text-color-child-active-hover-horizontal"]=t.itemTextColorChildActiveHoverHorizontalInverted,i["--n-item-text-color-active-hover-horizontal"]=t.itemTextColorActiveHoverHorizontalInverted,i["--n-item-icon-color-horizontal"]=t.itemIconColorHorizontalInverted,i["--n-item-icon-color-hover-horizontal"]=t.itemIconColorHoverHorizontalInverted,i["--n-item-icon-color-active-horizontal"]=t.itemIconColorActiveHorizontalInverted,i["--n-item-icon-color-active-hover-horizontal"]=t.itemIconColorActiveHoverHorizontalInverted,i["--n-item-icon-color-child-active-horizontal"]=t.itemIconColorChildActiveHorizontalInverted,i["--n-item-icon-color-child-active-hover-horizontal"]=t.itemIconColorChildActiveHoverHorizontalInverted,i["--n-arrow-color"]=t.arrowColorInverted,i["--n-arrow-color-hover"]=t.arrowColorHoverInverted,i["--n-arrow-color-active"]=t.arrowColorActiveInverted,i["--n-arrow-color-active-hover"]=t.arrowColorActiveHoverInverted,i["--n-arrow-color-child-active"]=t.arrowColorChildActiveInverted,i["--n-arrow-color-child-active-hover"]=t.arrowColorChildActiveHoverInverted,i["--n-item-color-hover"]=t.itemColorHoverInverted,i["--n-item-color-active"]=t.itemColorActiveInverted,i["--n-item-color-active-hover"]=t.itemColorActiveHoverInverted,i["--n-item-color-active-collapsed"]=t.itemColorActiveCollapsedInverted):(i["--n-group-text-color"]=t.groupTextColor,i["--n-color"]=t.color,i["--n-item-text-color"]=t.itemTextColor,i["--n-item-text-color-hover"]=t.itemTextColorHover,i["--n-item-text-color-active"]=t.itemTextColorActive,i["--n-item-text-color-child-active"]=t.itemTextColorChildActive,i["--n-item-text-color-child-active-hover"]=t.itemTextColorChildActiveHover,i["--n-item-text-color-active-hover"]=t.itemTextColorActiveHover,i["--n-item-icon-color"]=t.itemIconColor,i["--n-item-icon-color-hover"]=t.itemIconColorHover,i["--n-item-icon-color-active"]=t.itemIconColorActive,i["--n-item-icon-color-active-hover"]=t.itemIconColorActiveHover,i["--n-item-icon-color-child-active"]=t.itemIconColorChildActive,i["--n-item-icon-color-child-active-hover"]=t.itemIconColorChildActiveHover,i["--n-item-icon-color-collapsed"]=t.itemIconColorCollapsed,i["--n-item-text-color-horizontal"]=t.itemTextColorHorizontal,i["--n-item-text-color-hover-horizontal"]=t.itemTextColorHoverHorizontal,i["--n-item-text-color-active-horizontal"]=t.itemTextColorActiveHorizontal,i["--n-item-text-color-child-active-horizontal"]=t.itemTextColorChildActiveHorizontal,i["--n-item-text-color-child-active-hover-horizontal"]=t.itemTextColorChildActiveHoverHorizontal,i["--n-item-text-color-active-hover-horizontal"]=t.itemTextColorActiveHoverHorizontal,i["--n-item-icon-color-horizontal"]=t.itemIconColorHorizontal,i["--n-item-icon-color-hover-horizontal"]=t.itemIconColorHoverHorizontal,i["--n-item-icon-color-active-horizontal"]=t.itemIconColorActiveHorizontal,i["--n-item-icon-color-active-hover-horizontal"]=t.itemIconColorActiveHoverHorizontal,i["--n-item-icon-color-child-active-horizontal"]=t.itemIconColorChildActiveHorizontal,i["--n-item-icon-color-child-active-hover-horizontal"]=t.itemIconColorChildActiveHoverHorizontal,i["--n-arrow-color"]=t.arrowColor,i["--n-arrow-color-hover"]=t.arrowColorHover,i["--n-arrow-color-active"]=t.arrowColorActive,i["--n-arrow-color-active-hover"]=t.arrowColorActiveHover,i["--n-arrow-color-child-active"]=t.arrowColorChildActive,i["--n-arrow-color-child-active-hover"]=t.arrowColorChildActiveHover,i["--n-item-color-hover"]=t.itemColorHover,i["--n-item-color-active"]=t.itemColorActive,i["--n-item-color-active-hover"]=t.itemColorActiveHover,i["--n-item-color-active-collapsed"]=t.itemColorActiveCollapsed),i}),x=o?ge("menu",y(()=>e.inverted?"a":"b"),b,e):void 0,K=ro(),F=R(null),ie=R(null);let T=!0;const ke=()=>{var u;T?T=!1:(u=F.value)===null||u===void 0||u.sync({showAllItemsBeforeCalculate:!0})};function Fe(){return document.getElementById(K)}const J=R(-1);function Le(u){J.value=e.options.length-u}function Ke(u){u||(J.value=-1)}const Ve=y(()=>{const u=J.value;return{children:u===-1?[]:e.options.slice(u)}}),De=y(()=>{const{childrenField:u,disabledField:g,keyField:t}=e;return se([Ve.value],{getIgnored(p){return pe(p)},getChildren(p){return p[u]},getDisabled(p){return p[g]},getKey(p){var z;return(z=p[t])!==null&&z!==void 0?z:p.name}})}),Ue=y(()=>se([{}]).treeNodes[0]);function Ge(){var u;if(J.value===-1)return c(me,{root:!0,level:0,key:"__ellpisisGroupPlaceholder__",internalKey:"__ellpisisGroupPlaceholder__",title:"···",tmNode:Ue.value,domId:K,isEllipsisPlaceholder:!0});const g=De.value.treeNodes[0],t=j.value,p=!!(!((u=g.children)===null||u===void 0)&&u.some(z=>t.includes(z.key)));return c(me,{level:0,root:!0,key:"__ellpisisGroup__",internalKey:"__ellpisisGroup__",title:"···",virtualChildActive:p,tmNode:g,domId:K,rawNodes:g.rawNode.children||[],tmNodes:g.children||[],isEllipsisPlaceholder:!0})}return{mergedClsPrefix:r,controlledExpandedKeys:_,uncontrolledExpanededKeys:f,mergedExpandedKeys:P,uncontrolledValue:A,mergedValue:H,activePath:j,tmNodes:O,mergedTheme:n,mergedCollapsed:l,cssVars:o?void 0:b,themeClass:x==null?void 0:x.themeClass,overflowRef:F,counterRef:ie,updateCounter:()=>{},onResize:ke,onUpdateOverflow:Ke,onUpdateCount:Le,renderCounter:Ge,getCounter:Fe,onRender:x==null?void 0:x.onRender,showOption:k,deriveResponsiveState:ke}},render(){const{mergedClsPrefix:e,mode:r,themeClass:o,onRender:n}=this;n==null||n();const a=()=>this.tmNodes.map(d=>Ie(d,this.$props)),m=r==="horizontal"&&this.responsive,h=()=>c("div",io(this.$attrs,{role:r==="horizontal"?"menubar":"menu",class:[`${e}-menu`,o,`${e}-menu--${r}`,m&&`${e}-menu--responsive`,this.mergedCollapsed&&`${e}-menu--collapsed`],style:this.cssVars}),m?c(wo,{ref:"overflowRef",onUpdateOverflow:this.onUpdateOverflow,getCounter:this.getCounter,onUpdateCount:this.onUpdateCount,updateCounter:this.updateCounter,style:{width:"100%",display:"flex",overflow:"hidden"}},{default:a,counter:this.renderCounter}):a());return m?c(no,{onResize:this.onResize},{default:h}):h()}}),qo="/assets/logo-DnrCYlPZ.png",Yo=S({setup(){return()=>I(ne,{justify:"start",align:"center",class:"h-16"},{default:()=>[I("img",{src:qo,class:"h-12 w-12 ml-6"},null),I("h2",{class:"text-2xl font-bold"},[ve("Apache SeaTunnel")])]})}}),Wo=S({setup(){const e=lo({});return ho.getOverview().then(r=>Object.assign(e,r)),{data:e}},render(){return I(ne,{justify:"center",align:"center",wrap:!1,class:"h-16 mr-6"},{default:()=>[I("h2",{class:"text-base font-bold"},[ve("Version:")]),I("span",{class:"text-base text-nowrap"},[this.data.projectVersion]),I("h2",{class:"text-base font-bold ml-4"},[ve("Commit:")]),I("span",{class:"text-base text-nowrap"},[this.data.gitCommitAbbrev])]})}}),Xo=S({setup(){const e=mo().value.primaryColor;return()=>I(ne,{justify:"space-between",class:"h-16 border-gray-200 text-white",style:`background-color:${e}`},{default:()=>[I(Yo,null,null),I(Wo,null,null)]})}}),Zo={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},Jo=B("rect",{x:"32",y:"64",width:"448",height:"320",rx:"32",ry:"32",fill:"none",stroke:"currentColor","stroke-linejoin":"round","stroke-width":"32"},null,-1),Qo=B("path",{stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M304 448l-8-64h-80l-8 64h96z",fill:"currentColor"},null,-1),et=B("path",{fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32",d:"M368 448H144"},null,-1),ot=B("path",{d:"M32 304v48a32.09 32.09 0 0 0 32 32h384a32.09 32.09 0 0 0 32-32v-48zm224 64a16 16 0 1 1 16-16a16 16 0 0 1-16 16z",fill:"currentColor"},null,-1),tt=[Jo,Qo,et,ot],rt=S({name:"DesktopOutline",render:function(r,o){return te(),re("svg",Zo,tt)}}),nt={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},it=ao('<path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M160 144h288"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M160 256h288"></path><path fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32" d="M160 368h288"></path><circle cx="80" cy="144" r="16" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle><circle cx="80" cy="256" r="16" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle><circle cx="80" cy="368" r="16" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="32"></circle>',6),lt=[it],at=S({name:"ListOutline",render:function(r,o){return te(),re("svg",nt,lt)}}),ct={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},st=B("path",{d:"M402 168c-2.93 40.67-33.1 72-66 72s-63.12-31.32-66-72c-3-42.31 26.37-72 66-72s69 30.46 66 72z",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),dt=B("path",{d:"M336 304c-65.17 0-127.84 32.37-143.54 95.41c-2.08 8.34 3.15 16.59 11.72 16.59h263.65c8.57 0 13.77-8.25 11.72-16.59C463.85 335.36 401.18 304 336 304z",fill:"none",stroke:"currentColor","stroke-miterlimit":"10","stroke-width":"32"},null,-1),ut=B("path",{d:"M200 185.94c-2.34 32.48-26.72 58.06-53 58.06s-50.7-25.57-53-58.06C91.61 152.15 115.34 128 147 128s55.39 24.77 53 57.94z",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),vt=B("path",{d:"M206 306c-18.05-8.27-37.93-11.45-59-11.45c-52 0-102.1 25.85-114.65 76.2c-1.65 6.66 2.53 13.25 9.37 13.25H154",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-miterlimit":"10","stroke-width":"32"},null,-1),ht=[st,dt,ut,vt],mt=S({name:"PeopleOutline",render:function(r,o){return te(),re("svg",ct,ht)}}),pt={xmlns:"http://www.w3.org/2000/svg","xmlns:xlink":"http://www.w3.org/1999/xlink",viewBox:"0 0 512 512"},ft=B("path",{d:"M344 144c-3.92 52.87-44 96-88 96s-84.15-43.12-88-96c-4-55 35-96 88-96s92 42 88 96z",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"},null,-1),gt=B("path",{d:"M256 304c-87 0-175.3 48-191.64 138.6C62.39 453.52 68.57 464 80 464h352c11.44 0 17.62-10.48 15.65-21.4C431.3 352 343 304 256 304z",fill:"none",stroke:"currentColor","stroke-miterlimit":"10","stroke-width":"32"},null,-1),bt=[ft,gt],xt=S({name:"PersonOutline",render:function(r,o){return te(),re("svg",pt,bt)}}),Ct=S({name:"Sidebar",props:{sideKey:{type:String,default:""}},setup(){const e=R(!1),r=[""],o=_e(),{t:n}=co(),a=R(!1);function l(h){return()=>c(zo,null,{default:()=>c(h)})}const m=R([{label:()=>c(Q,{to:{path:"/overview"},exact:!1},{default:()=>n("menu.overview")}),key:"overview",icon:l(rt)},{label:()=>c(Q,{to:{path:"/jobs"},exact:!1},{default:()=>n("menu.jobs")}),key:"jobs",icon:l(at)},{label:()=>c(Q,{to:{path:"/managers/workers"},exact:!1},{default:()=>n("menu.managers.workers")}),key:"workers",icon:l(mt)},{label:()=>c(Q,{to:{path:"/managers/master"},exact:!1},{default:()=>n("menu.managers.master")}),key:"master",icon:l(xt)}]);return so(()=>{}),{collapsedRef:e,defaultExpandedKeys:r,showDrop:a,sideMenuOptions:m,route:o}},render(){return I(Mo,{bordered:!0,nativeScrollbar:!1,"show-trigger":"bar","collapse-mode":"width",collapsed:this.collapsedRef,onCollapse:()=>this.collapsedRef=!0,onExpand:()=>this.collapsedRef=!1,width:196},{default:()=>[I(Go,{class:"tab-vertical",value:this.$props.sideKey,options:this.sideMenuOptions,defaultExpandedKeys:this.defaultExpandedKeys},null)]})}}),St=S({setup(){const e=_e(),r=R(e.fullPath),o=R(!1),n=R(e.meta.activeMenu);return uo(()=>e,()=>{var a;o.value=(a=e==null?void 0:e.meta)==null?void 0:a.showSide,n.value=e.meta.activeSide,r.value=e.fullPath},{immediate:!0,deep:!0}),{showSide:o,menuKey:n,routeKey:r}},render(){return I(He,null,{default:()=>[I(Po,{bordered:!0},{default:()=>[I(Xo,null,null)]}),I(Ae,{style:{height:"calc(100vh - 69px)"}},{default:()=>[I(He,{"has-sider":!0,position:"absolute"},{default:()=>[this.showSide&&I(Ct,{sideKey:this.menuKey},null),I(Ae,{"native-scrollbar":!1},{default:()=>[I(ne,{vertical:!0,justify:"space-between",style:"height: 100%;padding: 16px 22px",size:"small"},{default:()=>[I(vo("router-view"),{key:this.routeKey,class:!this.showSide&&"px-32 py-12"},null)]})]})]})]})]})}});export{St as default};
