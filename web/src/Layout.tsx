import React from "react";
import {Grid, GridItem} from "@chakra-ui/react";


export type LayoutProps = {
    leftTop: React.ReactNode;
    leftBottom: React.ReactNode;
    right: React.ReactNode;
};

export const Layout: React.FC<LayoutProps> = ({leftTop, leftBottom, right}) => {
    return (
        <Grid templateColumns="65% 35%" templateRows="60% 40%" gap={1} height="100%" width="100%">
            <GridItem border="1px solid gray">
                {leftTop}
            </GridItem>
            <GridItem rowSpan={2} border="1px solid gray">
                {right}
            </GridItem>
            <GridItem border="1px solid gray">
                {leftBottom}
            </GridItem>
        </Grid>
    );
};
